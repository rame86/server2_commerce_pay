// src/main/java/com/example/payment/service/PaymentServiceImpl.java
package com.example.payment.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.Charge;
import com.example.payment.domain.Wallet;
import com.example.payment.dto.request.PaymentRequestDTO;
import com.example.payment.dto.response.PaymentReadyResponseDTO;
import com.example.payment.repository.ChargeRepository;
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

    // 지갑충전 준비(ready)
    @Override
    @Transactional
    public PaymentReadyResponseDTO readyPayment(Long memberId, PaymentRequestDTO request) {

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
                .amount(request.getChargeAmount())
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();

        chargeRepository.save(charge); // 충전 원장 DB 입력

        
        try {
            // 5. DTO 변환 후 Provider 위임
            PaymentReadyResponseDTO responseDTO = selectedProvider.ready(charge);

            // 변경 감지(Dirty Checking)로 UPDATE 자동 실행
            charge.updateTid(responseDTO.providerTid());
            return responseDTO;
        } catch (Exception e) {
            charge.fail(e.getMessage());            
            log.error("[Payment Error] 결제 준비 실패. chargeId: {}", charge.getChargeId(), e);
            throw new RuntimeException("결제 준비 실패: " + e.getMessage());
        }
        
    }

    // 실제 지갑충전 시작
    @Override
    @Transactional // 원장 상태 변경과 지갑 잔액 추가는 원자적으로 처리되어야 함
    public void approvePayment(UUID chargeId, String pgToken) {

        // 1. 대기 상태의 원장 조회 및 검증
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
            // 3. PG사 실승인 처리 요청
            selectedProvider.approve(charge, pgToken);

            // 승인 성공 시
            // 4. 충전 원장 성공 처리 및 지갑 잔액 추가
            // 변경 감지(Dirty Checking) 적용: 원장 상태 및 지갑 잔액 업데이트
            charge.success();
            Wallet wallet = walletRepository.findById(charge.getWalletId())
                    .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
            wallet.addBalance(charge.getAmount());

        } catch (Exception e) {
            // 승인 실패 시 원장을 실패 상태로 기록하여 무결성 유지 (보상 트랜잭션 등 추후 확장 포인트)
            charge.fail(e.getMessage());
            log.error("[Payment Approve Error] 승인 실패", e);
            throw new RuntimeException("결제 승인 실패: " + e.getMessage());
        }
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