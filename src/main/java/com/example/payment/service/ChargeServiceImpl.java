//src/main/java/com/example/payment/service/ChargeServiceImpl.java
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
import com.example.payment.dto.response.PaymentHistoryResponseDTO;
import com.example.payment.dto.response.TransactionDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;
import com.example.payment.repository.ChargeRepository;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.payment.repository.WalletRepository;
import com.example.payment.service.provider.PaymentProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeServiceImpl implements ChargeService {

    private final ChargeRepository chargeRepository;
    private final WalletRepository walletRepository;
    private final List<PaymentProvider> paymentProviders;
    private final WalletService walletService;
    private final PaymentEventProducer producer;
    private final TransactionHistoryRepository transactionHistoryRepository;

    @Lazy
    @Autowired
    private ChargeServiceImpl self; // 트랜잭션 전파(Propagation) 처리를 위한 자기 참조

    /**
     * [결제 충전 준비]
     * PG사 결제 요청 전, 지갑 상태를 검증하고 충전 원장(Charge)을 생성함
     */
    @Override
    @Transactional
    public ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request, String token) {
        log.info("[READY_PAYMENT] 요청 수신 - memberId: {}, amount: {}", memberId, request.getAmount());

        // 1. 사용자 지갑 조회 및 유효성 검증
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("지갑이 존재하지 않습니다."));

        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new IllegalArgumentException("유효하지 않은 지갑 상태입니다.");
        }

        // 2. 입력된 PG사 정보 표준화 및 전략(Provider) 선택
        String mappedPgProvider = resolvePgProvider(request.getPayType());
        PaymentProvider selectedProvider = paymentProviders.stream()
                .filter(provider -> provider.supports(mappedPgProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 결제 수단: " + mappedPgProvider));

        // 3. 결제 대기(PENDING) 상태의 원장 생성 및 저장
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
            // 4. PG사 외부 API 호출하여 결제 준비 완료 (TID 발급 등)
            ChargeReadyResponseDTO responseDTO = selectedProvider.ready(charge, memberId, token);
            charge.updateTid(responseDTO.providerTid());
            chargeRepository.save(charge);

            return responseDTO;
        } catch (Exception e) {
            charge.fail(e.getMessage()); // 실패 시 원장 상태 변경
            log.error("[READY_PAYMENT] 실패 - chargeId: {}", charge.getChargeId(), e);
            throw new RuntimeException("결제 준비 실패: " + e.getMessage());
        }
    }

    /**
     * [결제 승인 처리]
     * 사용자가 결제 인증을 마친 후, PG사에 실제 승인 확정 요청을 보냄
     */
    @Override
    public void approvePayment(UUID chargeId, String pgToken, String memberId) {
        log.info("[APPROVE_PAYMENT] 승인 요청 수신 - chargeId: {}", chargeId);

        // 1. 대기 중인 원장 확인
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 결제건입니다."));

        if (!"PENDING".equals(charge.getStatus())) {
            throw new IllegalArgumentException("이미 처리된 결제건입니다.");
        }

        // 2. 해당 PG사 Provider 선택
        PaymentProvider selectedProvider = paymentProviders.stream()
                .filter(provider -> provider.supports(charge.getPgProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 PG사: " + charge.getPgProvider()));

        try {
            // 3. PG사 외부 API 호출 (실제 결제 확정)
            selectedProvider.approve(charge, pgToken);

            // 4. 내부 DB 반영 (별도 트랜잭션 호출)
            self.processApprovalSuccess(chargeId, memberId);
        } catch (Exception e) {
            // 실패 시 별도 트랜잭션으로 실패 상태 기록
            self.processApprovalFail(chargeId, e.getMessage());
            log.error("[APPROVE_PAYMENT] 실패 - chargeId: {}", chargeId, e);
            throw new RuntimeException("결제 승인 실패: " + e.getMessage());
        }
    }

    /**
     * [결제 성공 후속 처리]
     * 원장 상태를 성공으로 바꾸고, 지갑 잔액을 실제로 충전하며 거래 내역을 남김
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processApprovalSuccess(UUID chargeId, String memberId) {
        // 1. 원장 성공 상태 변경
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("원장 조회 실패"));
        charge.success();

        // 2. 지갑 잔액 가산
        Wallet wallet = walletRepository.findById(charge.getWalletId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        wallet.addBalance(charge.getAmount());

        // 3. 거래 내역(History) 기록
        TransactionHistory txHistory = TransactionHistory.builder()
                .walletId(wallet.getWalletId())
                .transactionType("CHARGE")
                .amount(charge.getAmount())
                .balanceAfter(wallet.getBalance())
                .referenceId(chargeId.toString())
                .description(charge.getPgProvider() + " 충전")
                .build();
        transactionHistoryRepository.save(txHistory);

        // 4. Redis 잔액 동기화
        BigDecimal balance = walletService.getBalance(Long.valueOf(memberId));
        walletService.updateRedisBalance(Long.valueOf(memberId), balance);

        log.info("[APPROVE_PAYMENT] 원장 및 잔액 반영 완료");
    }

    /**
     * [결제 실패 후속 처리]
     * 승인 과정에서 오류가 발생한 경우 원장에 실패 사유를 기록함
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processApprovalFail(UUID chargeId, String errorMessage) {
        chargeRepository.findById(chargeId).ifPresent(charge -> {
            charge.fail(errorMessage);
        });
    }

    /**
     * [결제 내역 조회]
     * 지갑 잔액 및 포인트 증감 내역 반환. 지갑이 없으면 신규 생성.
     */
    @Override
    @Transactional // 지갑 생성이 발생할 수 있으므로 트랜잭션 처리
    public PaymentHistoryResponseDTO getPaymentHistory(Long memberId) {

        // 1. 지갑 조회, 없으면 즉시 생성 (초기 잔액 0, 상태 ACTIVE)
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    log.info("[WALLET_CREATE] 지갑 자동 생성 - memberId: {}", memberId);
                    Wallet newWallet = Wallet.builder()
                            .memberId(memberId)
                            .balance(BigDecimal.ZERO)
                            .status("ACTIVE")
                            .build();
                    return walletRepository.save(newWallet);
                });

        // 2. 거래 내역 최신순 조회
        List<TransactionHistory> histories = transactionHistoryRepository
                .findAllByWalletIdOrderByCreatedAtDesc(wallet.getWalletId());

        // 3. Entity -> DTO 변환
        List<TransactionDTO> transactionDTOs = histories.stream()
                .map(history -> TransactionDTO.builder()
                        .transactionType(history.getTransactionType())
                        .amount(history.getAmount())
                        .balanceAfter(history.getBalanceAfter())
                        .description(history.getDescription())
                        .createdAt(history.getCreatedAt())
                        .build())
                .toList();

        // 4. 최종 결과 조립
        return PaymentHistoryResponseDTO.builder()
                .currentBalance(wallet.getBalance())
                .transactions(transactionDTOs)
                .build();
    }

    /**
     * [PG 제공자 분석]
     * 입력받은 payType을 대문자로 정규화하여 지원하는 수단인지 확인 (White-list 기반)
     */
    private String resolvePgProvider(String payType) {
        if (payType == null || payType.isBlank()) {
            throw new IllegalArgumentException("결제 수단(payType)이 누락되었습니다.");
        }
        return switch (payType.toUpperCase()) {
            case "KAKAO_PAY" -> "KAKAO_PAY";
            case "NAVER_PAY" -> "NAVER_PAY";
            case "BANK_TRANSFER" -> "BANK_TRANSFER";
            case "CREDIT_CARD" -> "CREDIT_CARD";
            default -> throw new IllegalArgumentException("지원하지 않는 결제 수단입니다: " + payType);
        };
    }
}