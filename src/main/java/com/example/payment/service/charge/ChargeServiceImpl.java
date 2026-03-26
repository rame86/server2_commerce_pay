//src/main/java/com/example/payment/service/ChargeServiceImpl.java
package com.example.payment.service.charge;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.Charge;
import com.example.payment.domain.TransactionHistory;
import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;
import com.example.payment.dto.response.PaymentHistoryResponseDTO;
import com.example.payment.dto.response.TransactionDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;
import com.example.payment.repository.ChargeRepository;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.payment.service.charge.provider.PaymentProvider;
import com.example.wallet.domain.Wallet;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeServiceImpl implements ChargeService {

    private final ChargeRepository chargeRepository;
    private final WalletRepository walletRepository;
    
    // 전략 패턴(Strategy Pattern) 활용: 지원하는 모든 PG사 구현체를 리스트로 주입받음
    private final List<PaymentProvider> paymentProviders; 
    
    private final WalletService walletService;
    private final PaymentEventProducer producer;
    private final TransactionHistoryRepository transactionHistoryRepository;

    /**
     * Spring AOP 프록시 내부 호출(Self-Invocation) 문제 해결을 위한 자기 참조 주입.
     * approvePayment(일반 메서드) 안에서 @Transactional(REQUIRES_NEW)가 붙은 
     * processApprovalSuccess/Fail을 호출할 때 트랜잭션이 정상 작동하도록 @Lazy로 지연 주입받음.
     */
    @Lazy
    @Autowired
    private ChargeServiceImpl self; 

    /**
     * [결제 충전 준비]
     * PG사 결제창을 띄우기 전, 내부 시스템에 결제 원장을 '대기(PENDING)' 상태로 생성하고
     * PG사로부터 결제 고유 번호(TID)를 발급받는 단계입니다.
     */
    @Override
    @Transactional
    public ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request, String token) {
        log.info(">>> [READY_PAYMENT] 요청 수신 - memberId: {}, amount: {}", memberId, request.getAmount());

        // 1. 사용자 지갑 조회 및 유효성 검증 (지갑이 없거나 비활성 상태면 예외 처리)
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("지갑이 존재하지 않습니다."));

        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new IllegalArgumentException("유효하지 않은 지갑 상태입니다.");
        }

        // 2. PG사 프로바이더 라우팅
        // 입력받은 결제 수단을 표준화한 후, 해당 PG사를 처리할 수 있는 Provider 구현체를 찾음
        String mappedPgProvider = resolvePgProvider(request.getPayType());
        PaymentProvider selectedProvider = paymentProviders.stream()
                .filter(provider -> provider.supports(mappedPgProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 결제 수단: " + mappedPgProvider));

        // 3. 결제 대기(PENDING) 상태의 내부 원장(Charge) 생성
        // 아직 결제가 완료된 것이 아니므로 상태를 PENDING으로 설정
        Charge charge = Charge.builder()
                .chargeId(UUID.randomUUID())
                .walletId(wallet.getWalletId())
                .pgProvider(mappedPgProvider)
                .amount(request.getAmount())
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();

        chargeRepository.save(charge);

        try {
            // 4. PG사 외부 API 호출하여 결제 준비 완료 (PG사 측 TID 발급)
            ChargeReadyResponseDTO responseDTO = selectedProvider.ready(charge, memberId, token);
            
            // 발급받은 외부 PG사의 TID를 내부 원장에 매핑하여 업데이트
            charge.updateTid(responseDTO.providerTid());
            chargeRepository.save(charge);

            return responseDTO;
        } catch (Exception e) {
            // PG사 통신 실패 등 오류 발생 시 원장 상태를 실패(FAIL)로 즉시 변경
            charge.fail(e.getMessage()); 
            log.error(">>> [READY_PAYMENT] 실패 - chargeId: {}", charge.getChargeId(), e);
            throw new RuntimeException("결제 준비 실패: " + e.getMessage());
        }
    }

    /**
     * [결제 승인 처리]
     * 사용자가 PG사 결제창에서 인증을 마친 후 리다이렉트 되었을 때,
     * 실제 금액 출금을 위해 PG사에 '최종 승인'을 요청하는 단계입니다.
     */
    @Override
    public void approvePayment(UUID chargeId, String pgToken, String memberId) {
        log.info(">>> [APPROVE_PAYMENT] 승인 요청 수신 - chargeId: {}", chargeId);

        // 1. 내부 원장 무결성 검증 (해당 결제건이 존재하는지, 상태가 PENDING인지 확인)
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 결제건입니다."));

        if (!"PENDING".equals(charge.getStatus())) {
            throw new IllegalArgumentException("이미 처리된 결제건입니다.");
        }

        // 2. 원장에 기록된 PG사에 맞는 Provider 다시 선택
        PaymentProvider selectedProvider = paymentProviders.stream()
                .filter(provider -> provider.supports(charge.getPgProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 PG사: " + charge.getPgProvider()));

        try {
            // 3. PG사 외부 API 호출하여 최종 결제 승인(확정) 처리
            // 이 시점에 실제 고객의 계좌/카드에서 돈이 빠져나감
            selectedProvider.approve(charge, pgToken);

            // 4. 내부 DB 반영
            // 자기 참조(self)를 사용하여 REQUIRES_NEW 트랜잭션을 정상적으로 발생시킴
            self.processApprovalSuccess(chargeId, memberId);
        } catch (Exception e) {
            // 결제 승인 실패 시 실패 상태를 별도 트랜잭션으로 확실하게 DB에 기록
            self.processApprovalFail(chargeId, e.getMessage());
            log.error(">>> [APPROVE_PAYMENT] 실패 - chargeId: {}", chargeId, e);
            throw new RuntimeException("결제 승인 실패: " + e.getMessage());
        }
    }

    /**
     * [결제 성공 후속 처리]
     * REQUIRES_NEW: 호출한 쪽(approvePayment)에 트랜잭션이 없더라도 무조건 새로운 트랜잭션을 생성.
     * PG사 결제는 성공했는데 내부 DB 반영 중 에러가 나더라도 상태 일관성을 관리하기 위한 분리.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processApprovalSuccess(UUID chargeId, String memberId) {
        // 1. 원장 상태를 '성공(SUCCESS)'으로 변경
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("원장 조회 실패"));
        charge.success();

        // 2. 실제 사용자 지갑에 결제된 금액만큼 잔액 추가
        Wallet wallet = walletRepository.findById(charge.getWalletId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        wallet.addBalance(charge.getAmount());

        // 3. 거래 내역(History) 스냅샷 기록 (이후 감사 및 내역 조회용)
        TransactionHistory txHistory = TransactionHistory.builder()
                .walletId(wallet.getWalletId())
                .transactionType("CHARGE")
                .amount(charge.getAmount())
                .balanceAfter(wallet.getBalance()) // 금액 추가 후의 최종 잔액 기록
                .referenceId(chargeId.toString())
                .description(charge.getPgProvider() + " 충전")
                .build();
        transactionHistoryRepository.save(txHistory);

        // 4. Redis 캐시 데이터 동기화 (빠른 잔액 조회를 위해 RDB와 상태를 맞춤)
        BigDecimal balance = walletService.getBalance(Long.valueOf(memberId));
        walletService.updateRedisBalance(Long.valueOf(memberId), balance);

        log.info(">>> [APPROVE_PAYMENT] 원장 및 잔액 반영 완료");
    }

    /**
     * [결제 실패 후속 처리]
     * REQUIRES_NEW: 메인 로직에서 예외가 발생해 롤백되더라도, 실패 이력 자체는 무조건 DB에 남기기 위함.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processApprovalFail(UUID chargeId, String errorMessage) {
        chargeRepository.findById(chargeId).ifPresent(charge -> {
            charge.fail(errorMessage); // 원장 상태를 FAIL로 변경하고 사유 기록
        });
    }

    /**
     * [결제 내역 조회]
     * 사용자의 현재 지갑 잔액과 과거 거래 내역을 모두 반환합니다.
     */
    @Override
    @Transactional 
    public PaymentHistoryResponseDTO getPaymentHistory(Long memberId) {

        // 1. 지갑 조회. 회원의 지갑이 아직 없다면 초기 잔액 0원인 지갑을 즉시 생성 (Lazy Init)
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    log.info(">>> [WALLET_CREATE] 지갑 자동 생성 - memberId: {}", memberId);
                    Wallet newWallet = Wallet.builder()
                            .memberId(memberId)
                            .balance(BigDecimal.ZERO)
                            .status("ACTIVE")
                            .build();
                    return walletRepository.save(newWallet);
                });

        // 2. 해당 지갑의 거래 내역을 최신순(내림차순)으로 모두 조회
        List<TransactionHistory> histories = transactionHistoryRepository
                .findAllByWalletIdOrderByCreatedAtDesc(wallet.getWalletId());

        // 3. Entity 객체를 외부 노출용 DTO로 변환 (엔티티 직접 노출 방지)
        List<TransactionDTO> transactionDTOs = histories.stream()
                .map(history -> TransactionDTO.builder()
                        .transactionType(history.getTransactionType())
                        .amount(history.getAmount())
                        .balanceAfter(history.getBalanceAfter())
                        .description(history.getDescription())
                        .createdAt(history.getCreatedAt())
                        .build())
                .toList();

        // 4. 현재 잔액과 거래 내역 리스트를 묶어서 반환
        return PaymentHistoryResponseDTO.builder()
                .currentBalance(wallet.getBalance())
                .transactions(transactionDTOs)
                .build();
    }

    /**
     * [PG 제공자 분석]
     * 클라이언트로부터 전달받은 payType 문자열을 대문자로 정규화하고, 
     * 화이트리스트(White-list) 방식으로 검증하여 올바른 PG사 코드를 반환.
     */
    private String resolvePgProvider(String payType) {
        if (payType == null || payType.isBlank()) {
            throw new IllegalArgumentException("결제 수단(payType)이 누락되었습니다.");
        }
        
        // Java 14+ Enhanced Switch 구문 사용
        return switch (payType.toUpperCase()) {
            case "KAKAO_PAY" -> "KAKAO_PAY";
            case "NAVER_PAY" -> "NAVER_PAY";
            case "BANK_TRANSFER" -> "BANK_TRANSFER";
            case "CREDIT_CARD" -> "CREDIT_CARD";
            default -> throw new IllegalArgumentException("지원하지 않는 결제 수단입니다: " + payType);
        };
    }
}