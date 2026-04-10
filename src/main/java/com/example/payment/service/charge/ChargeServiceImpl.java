//src/main/java/com/example/payment/service/ChargeServiceImpl.java
package com.example.payment.service.charge;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.user.ChargeReadyResponseDTO;
import com.example.payment.dto.user.ChargeRequestDTO;
import com.example.payment.entity.Charge;
import com.example.payment.repository.ChargeRepository;
import com.example.payment.service.charge.provider.PaymentProvider;
import com.example.payment.service.record.PaymentRecordService;
import com.example.wallet.entity.Wallet;
import com.example.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeServiceImpl implements ChargeService {

    private final ChargeRepository chargeRepository;
    private final WalletRepository walletRepository;
    private final PaymentRecordService paymentRecordService; // 결제 승인 후 DB 반영을 위한 서비스 의존성 주입

    // 전략 패턴(Strategy Pattern) 활용: 지원하는 모든 PG사 구현체를 리스트로 주입받음
    private final List<PaymentProvider> paymentProviders;

    /**
     * [결제 충전 준비]
     */
    @Override
    @Transactional
    public ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request) {
        log.info(">>> [READY_PAYMENT] 요청 수신 - memberId: {}, amount: {}", memberId, request.getAmount());        

        // 1. 사용자 지갑 조회 및 유효성 검증 (지갑이 없거나 비활성 상태면 예외 처리)
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("지갑이 존재하지 않습니다."));

        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new IllegalArgumentException("유효하지 않은 지갑 상태입니다.");
        }

        // 2. PG사 프로바이더 라우팅
        // 입력받은 결제 수단을 표준화한 후, 해당 PG사를 처리할 수 있는 Provider 구현체를 찾음
        // PG사 코드 매핑 및 검증 (예: KAKAO_PAY, NAVER_PAY 등)
        String mappedPgProvider = resolvePgProvider(request.getPayType()); 

        // 지원하는 PG사 구현체 중에서 매핑된 PG사 코드를 처리할 수 있는 Provider를 찾음        
        // .stream() 호출하여 paymentProviders 리스트를 '흐름(Stream)'으로 변환한 후,
        // .filter() 메소드에 supports() 메서드를 람다식으로 전달하여 지원하는 Provider만 필터링
        PaymentProvider selectedProvider = paymentProviders.stream()
                // 각 Provider 구현체에서 자신이 처리할 수 있는지 판단하는 supports() 메서드를 호출하여 필터링
                // true일때만 다음식으로 넘어가고, false인 경우는 걸러짐
                .filter(provider -> provider.supports(mappedPgProvider)) 
                // 찾은 Provider가 있으면 반환, 없으면 예외 발생
                // Provider가 없을수 있기에 null이 반환될 수 있으므로 .findeFirst()로 Optional로 감싸서 처리
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 결제 수단: " + mappedPgProvider));
                // 람다식을 사용하여 예외 객체를 미리 생성하지 않고, 실제로 예외가 필요한 시점에만 생성하여 메모리 효율성 향상

        // 3. 결제 대기(PENDING) 상태의 내부 원장(Charge) 생성
        // 아직 결제가 완료된 것이 아니므로 상태를 PENDING으로 설정
        Charge charge = Charge.builder() // 빌더 패턴으로 Charge 객체 생성
                .chargeId(UUID.randomUUID()) // 고유한 결제 ID 생성
                .walletId(wallet.getWalletId()) // 해당 회원의 지갑 ID 참조
                .pgProvider(mappedPgProvider) // PG사 코드 저장 (예: KAKAO_PAY, NAVER_PAY)
                .amount(request.getAmount()) // 결제 요청 금액
                .status("PENDING") // 초기 상태는 PENDING으로 설정
                .createdAt(OffsetDateTime.now()) // 생성 시점 기록
                .build();

        chargeRepository.save(charge); // 원장 DB에 저장 (이 시점에서는 아직 PG사 TID가 없음)

        try {
            // 4. PG사 외부 API 호출하여 결제 준비 완료 (PG사 측 TID 발급)
            ChargeReadyResponseDTO responseDTO = selectedProvider.ready(charge, memberId);

            // 발급받은 외부 PG사의 TID를 내부 원장에 매핑하여 업데이트
            charge.updateTid(responseDTO.providerTid());
            chargeRepository.save(charge);

            return responseDTO; // 클라이언트에게 결제창 URL과 TID를 포함한 응답 반환
        } catch (Exception e) {
            // PG사 통신 실패 등 오류 발생 시 원장 상태를 실패(FAIL)로 즉시 변경
            charge.fail(e.getMessage());
            log.error(">>> [READY_PAYMENT] 실패 - chargeId: {}", charge.getChargeId(), e);
            throw new RuntimeException("결제 준비 실패: " + e.getMessage());
        }
    }

    /**
     * [결제 승인 처리]
     */
    @Override
    public void approvePayment(UUID chargeId, String pgToken, String memberId) {
        log.info(">>> [APPROVE_PAYMENT] 승인 요청 수신 - chargeId: {}", chargeId);

        // 1. 내부 원장 무결성 검증 (해당 결제건이 존재하는지, 상태가 PENDING인지 확인)
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 결제건입니다."));

        if (!"PENDING".equals(charge.getStatus())) { // 이미 처리된 결제건은 승인 로직을 수행할 수 없도록 방어적 코딩
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
            paymentRecordService.processApprovalSuccess(chargeId, memberId);
        } catch (Exception e) {
            // 결제 승인 실패 시 실패 상태를 별도 트랜잭션으로 확실하게 DB에 기록
            paymentRecordService.processApprovalFail(chargeId, e.getMessage());
            log.error(">>> [APPROVE_PAYMENT] 실패 - chargeId: {}", chargeId, e);
            throw new RuntimeException("결제 승인 실패: " + e.getMessage());
        }
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
            case "kakao_pay" -> "KAKAO_PAY";
            case "naver_pay" -> "NAVER_PAY";
            case "bank_transfer" -> "BANK_TRANSFER";
            case "credit_card" -> "CREDIT_CARD";
            default -> throw new IllegalArgumentException("지원하지 않는 결제 수단입니다: " + payType);
        };
    }
}