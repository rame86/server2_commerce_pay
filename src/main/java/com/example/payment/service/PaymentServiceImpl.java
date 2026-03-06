// src/main/java/com/example/payment/service/PaymentServiceImpl.java
package com.example.payment.service;

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
import com.example.payment.domain.Wallet;
import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;
import com.example.payment.repository.ChargeRepository;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.payment.repository.WalletRepository;
import com.example.payment.service.provider.PaymentProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final ChargeRepository chargeRepository;
    private final WalletRepository walletRepository;
    private final List<PaymentProvider> paymentProviders;
    private final WalletService walletService;
    // [ADD] 거래 내역 저장을 위한 의존성 추가
    private final TransactionHistoryRepository transactionHistoryRepository;

    // AOP 프록시를 통한 내부 메서드 호출(트랜잭션 분리)을 위해 자기 자신 주입
    @Lazy
    @Autowired
    private PaymentServiceImpl self;

    // [READY_PAYMENT] 지갑충전 준비
    @Override
    @Transactional
    public ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request) {
        // [LOG] 요청 진입
        log.info("[READY_PAYMENT] 요청 수신 - memberId: {}, payType: {}, amount: {}", 
                 memberId, request.getPayType(), request.getAmount());

        // 1. 지갑 검증
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("지갑이 존재하지 않습니다."));

        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new IllegalArgumentException("유효하지 않은 지갑 상태입니다.");
        }

        // 2. 입력값 정규화 및 화이트리스트 검증 (Self-Review 적용)
        String mappedPgProvider = resolvePgProvider(request.getPayType());

        // 3. 전략 패턴을 통한 동적 Provider 선택 (OCP 준수)
        PaymentProvider selectedProvider = paymentProviders.stream()
                .filter(provider -> provider.supports(mappedPgProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("현재 서비스 불가능한 결제 수단입니다: " + mappedPgProvider));

        // 4. 충전 원장 생성 (정규화된 KAKAO_PAY, NAVER_PAY 등이 들어감)
        // JPA 엔티티 저장
        Charge charge = Charge.builder()
                .chargeId(UUID.randomUUID())
                .walletId(wallet.getWalletId())
                .pgProvider(mappedPgProvider)
                .amount(request.getAmount())
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();

        chargeRepository.save(charge); // 충전 원장 DB 입력
        // [LOG] 원장 DB입력
        log.info("[READY_PAYMENT] 충전 원장 DB입력 완료");
        try {
            // 5. DTO 변환 후 Provider 위임
            ChargeReadyResponseDTO responseDTO = selectedProvider.ready(charge, memberId);

            // 변경 감지(Dirty Checking)로 UPDATE 자동 실행
            // (UUID 수동 할당으로 인해 merge 동작하므로 명시적 save 재호출 유지)
            charge.updateTid(responseDTO.providerTid());
            chargeRepository.save(charge);

            // [LOG] 응답 반환
            log.info("[READY_PAYMENT] 준비 완료 - chargeId: {}, providerTid: {}", 
                     responseDTO.chargeId(), responseDTO.providerTid());
            
            return responseDTO;
        } catch (Exception e) {
            charge.fail(e.getMessage());            
            log.error("[READY_PAYMENT] 에러 발생 - chargeId: {}", charge.getChargeId(), e);
            throw new RuntimeException("결제 준비 실패: " + e.getMessage());
        }
    }

    // [APPROVE_PAYMENT] 실제 지갑충전 시작
    @Override    
    public void approvePayment(UUID chargeId, String pgToken, String memberId) {
        // [LOG] 요청 진입
        log.info("[APPROVE_PAYMENT] 승인 요청 수신 - chargeId: {}", chargeId);

        // 1. 대기 상태의 원장 조회 및 검증 (외부 통신 전 트랜잭션 없이 조회)
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 결제건입니다."));

        if (!"PENDING".equals(charge.getStatus())) {
            throw new IllegalArgumentException("이미 처리된 결제건입니다.");
        }

        // 2. 동적 Provider 선택
        PaymentProvider selectedProvider = paymentProviders.stream()
                .filter(provider -> provider.supports(charge.getPgProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 PG사: " + charge.getPgProvider()));

        try {
            // 3. PG사 실승인 처리 요청 (트랜잭션 밖에서 실행)
            selectedProvider.approve(charge, pgToken);

            // 승인 성공 시
            // 4. 충전 원장 성공 처리 및 지갑 잔액 추가 (별도 트랜잭션)
            self.processApprovalSuccess(chargeId, memberId);

        } catch (Exception e) {
            // 승인 실패 시 원장을 실패 상태로 기록하여 무결성 유지 (보상 트랜잭션 등 추후 확장 포인트)
            // (독립 트랜잭션으로 예외 발생 시에도 FAILED 상태 커밋 보장)
            self.processApprovalFail(chargeId, e.getMessage());
            log.error("[APPROVE_PAYMENT] 승인 실패 - chargeId: {}", chargeId, e);
            throw new RuntimeException("결제 승인 실패: " + e.getMessage());
        }
    }

    // [SUCCESS_TRANSACTION] 승인 성공 시 원장 상태, 지갑 잔액, 거래 내역 업데이트 로직
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processApprovalSuccess(UUID chargeId, String memberId) {
        // 1. 충전 원장 성공 처리
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("원장 조회 실패"));
        charge.success();

        // 2. 지갑 잔액 증가 처리
        Wallet wallet = walletRepository.findById(charge.getWalletId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        wallet.addBalance(charge.getAmount());

        // 3. 거래 내역(TransactionHistory) 기록 추가
        TransactionHistory txHistory = TransactionHistory.builder()
                .walletId(wallet.getWalletId())
                .transactionType("CHARGE") // 충전 타입 명시
                .amount(charge.getAmount())
                .balanceAfter(wallet.getBalance()) // 증가된 최종 잔액 기록
                .referenceId(chargeId.toString()) // Charge ID를 참조키로 사용
                .description("카카오페이 충전") // 필요 시 동적으로 변경 가능
                .build();
        transactionHistoryRepository.save(txHistory);

        // 4. Redis 잔액 동기화
        BigDecimal balance = walletService.getBalance(Long.valueOf(memberId));
        walletService.updateRedisBalance(Long.valueOf(memberId), balance);

        // [LOG] 승인 완료
        log.info("[APPROVE_PAYMENT] 최종 승인 및 잔액/내역 반영 완료 - chargeId: {}, addedAmount: {}, balance: {}", 
                 chargeId, charge.getAmount(), balance);
    }

    // [FAIL_TRANSACTION] 승인 실패 시 원장 상태 업데이트 로직
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processApprovalFail(UUID chargeId, String errorMessage) {
        chargeRepository.findById(chargeId).ifPresent(charge -> {
            charge.fail(errorMessage);
        });
    }

    // 클라이언트의 입력값을 표준 포맷으로 변환하고 허용되지 않은 수단은 차단.
    private String resolvePgProvider(String payType) {
        if (payType == null || payType.isBlank()) {
            throw new IllegalArgumentException("결제 수단(payType)이 누락되었습니다.");
        }

        return switch (payType.toLowerCase()) {
            case "kakao_pay" -> "KAKAO_PAY";
            case "naver_pay" -> "NAVER_PAY";
            case "bank_transfer" -> "BANK_TRANSFER";
            case "credit_card" -> "CREDIT_CARD";
            default -> throw new IllegalArgumentException("지원하지 않는 결제 수단입니다: " + payType);
        };
    }
}