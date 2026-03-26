// src/main/java/com/example/payment/service/WalletServiceImpl.java
package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.TransactionHistory;
import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.wallet.domain.Wallet;
import com.example.wallet.dto.WalletDTO;
import com.example.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [지갑 비즈니스 로직 구현체]
 * 포인트 차감/적립, 결제 및 환불 이력 관리, 캐시(Redis) 잔액 동기화를 수행함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionHistoryRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * [전체 지갑 조회]
     * 시스템에 등록된 모든 지갑 정보를 DTO 리스트로 반환함.
     */
    @Override
    @Transactional(readOnly = true)
    public List<WalletDTO> getAllWallets() {
        log.info(">>> [WALLET_ALL] 전체 지갑 목록 조회 요청");
        return walletRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * [지갑 조회 및 자동 생성 (Internal)]
     * 지갑이 존재하지 않는 회원의 경우, 초기 잔액 0원인 지갑을 즉시 생성함 (Lazy Initialization).
     */
    private Wallet getOrCreateWallet(Long memberId) {
        log.info(">>> [WALLET_GET_OR_CREATE] 지갑 정보 확인 - MemberId: {}", memberId);
        return walletRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    log.info(">>> [WALLET_CREATE] 신규 지갑 자동 생성 - MemberId: {}", memberId);
                    Wallet newWallet = Wallet.builder()
                            .memberId(memberId)
                            .balance(BigDecimal.ZERO)
                            .status("ACTIVE")
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    /**
     * [현재 잔액 조회]
     * 특정 회원의 실시간 지갑 잔액을 반환함.
     */
    @Override
    @Transactional
    public BigDecimal getBalance(Long memberId) {
        log.info(">>> [WALLET_BALANCE] 잔액 확인 요청 - MemberId: {}", memberId);
        return getOrCreateWallet(memberId).getBalance();
    }

    /**
     * [결제 처리 (포인트 차감)]
     * 주문 이벤트를 수신하여 사용자의 포인트를 차감하고 거래 이력을 기록함.
     */
    @Override
    @Transactional
    public void processPayment(PaymentEventDTO dto) {
        log.info(">>> [PAYMENT_PROCESS] 결제 포인트 차감 시작 - OrderId: {}, Amount: {}", dto.getOrderId(), dto.getAmount());

        // 1. 지갑 확보 및 상태 검증
        Wallet wallet = getOrCreateWallet(dto.getMemberId());
        if (!"ACTIVE".equals(wallet.getStatus())) {
            log.error(">>> [PAYMENT_FAIL] 지갑 비활성 상태 - WalletId: {}", wallet.getWalletId());
            throw new IllegalStateException("유효하지 않은 지갑 상태입니다.");
        }

        // 2. 잔액 검증 및 차감 (낙관적 락 적용됨)
        // Entity 내부의 deductBalance 메서드에서 잔액 부족 체크 수행
        wallet.deductBalance(dto.getAmount());
        BigDecimal newBalance = wallet.getBalance();

        // 3. 거래 이력(원장) 저장
        String paymentDesc = "결제 - " + (dto.getEventTitle() != null ? dto.getEventTitle() : "상품 결제");
        recordTransaction(
                wallet.getWalletId(),
                dto.getType(),
                dto.getAmount().negate(), // 차감액이므로 음수 기록
                newBalance,
                dto.getOrderId(),
                paymentDesc,
                dto.getOriginalPrice(),
                dto.getFee(),
                dto.getShippingFee(),
                dto.getQuantity(),
                dto.getArtistId());

        log.info(">>> [PAYMENT_SUCCESS] 결제 차감 완료 - New Balance: {}", newBalance);
    }

    /**
     * [환불 처리 (포인트 복구)]
     * 결제 취소 이벤트를 수신하여 차감되었던 금액을 지갑에 다시 예치함.
     */
    @Override
    @Transactional
    public void processRefund(PaymentEventDTO dto) {
        log.info(">>> [REFUND_PROCESS] 환불 처리 시작 - OrderId: {}", dto.getOrderId());

        // 1. 원본 결제 내역 존재 여부 확인
        TransactionHistory paymentTx = transactionRepository.findTopByReferenceIdAndTransactionType(dto.getOrderId(), "PAYMENT");
        if (paymentTx == null) {
            log.error(">>> [REFUND_FAIL] 원본 결제 데이터 없음 - OrderId: {}", dto.getOrderId());
            throw new IllegalArgumentException("원본 결제 내역을 찾을 수 없습니다.");
        }

        // 2. 멱등성 검증 (중복 환불 방지)
        if (transactionRepository.existsByReferenceIdAndTransactionType(dto.getOrderId(), "REFUND")) {
            log.warn(">>> [REFUND_SKIP] 이미 완료된 환불 요청 - OrderId: {}", dto.getOrderId());
            return;
        }

        // 3. 금액 복구 (Dirty Checking 활용)
        BigDecimal refundAmount = paymentTx.getAmount().abs(); // 저장된 음수 금액을 양수로 변환
        Wallet wallet = walletRepository.findById(paymentTx.getWalletId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        wallet.addBalance(refundAmount);
        BigDecimal newBalance = wallet.getBalance();

        // 4. 환불 이력 저장
        // 원본 결제 내역의 description에서 "결제 - " 접두사를 제거 후 "환불 - " 접두사로 대체
        String originalDesc = paymentTx.getDescription() != null ? paymentTx.getDescription() : "결제 취소";
        String refundDesc;
        if (originalDesc.startsWith("결제 - ")) {
            refundDesc = "환불 - " + originalDesc.substring("결제 - ".length());
        } else {
            refundDesc = "환불 - " + originalDesc;
        }
        recordTransaction(
                wallet.getWalletId(),
                dto.getType(),
                refundAmount,
                newBalance,
                dto.getOrderId(),
                refundDesc,
                dto.getOriginalPrice(),
                dto.getFee(),
                dto.getShippingFee(),
                dto.getQuantity(),
                dto.getArtistId());

        log.info(">>> [REFUND_SUCCESS] 포인트 복구 완료 - Refund: {}, New Balance: {}", refundAmount, newBalance);
    }

    /**
     * [거래 이력 기록 공통 로직]
     */
    private void recordTransaction(UUID walletId, String type, BigDecimal amount, BigDecimal balanceAfter,
                                  String referenceId, String description, BigDecimal originalAmount, BigDecimal fee,
                                  BigDecimal shippingFee, Integer quantity, Long artistId) {
        log.debug(">>> [TX_RECORD] 거래 내역 생성 중 - RefId: {}, Type: {}", referenceId, type);
        TransactionHistory th = TransactionHistory.builder()
                .walletId(walletId)
                .transactionType(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .referenceId(referenceId)
                .description(description)
                .originalAmount(originalAmount)
                .fee(fee)
                .shippingFee(shippingFee)
                .quantity(quantity)
                .artistId(artistId)
                .build();

        transactionRepository.save(th);
    }

    /**
     * [엔티티 -> DTO 변환]
     */
    private WalletDTO convertToResponseDTO(Wallet wallet) {
        return WalletDTO.builder()
                .walletId(wallet.getWalletId())
                .memberId(wallet.getMemberId())
                .balance(wallet.getBalance())
                .status(wallet.getStatus())
                .version(wallet.getVersion() != null ? wallet.getVersion() : 0)
                .build();
    }

    /**
     * [Redis 잔액 캐시 동기화]
     * DB 트랜잭션 성공 후 호출되어, 인증 서비스(Core) 등에서 활용하는 잔액 캐시 정보를 업데이트함.
     */
    @Override
    public void updateRedisBalance(Long memberId, BigDecimal balance) {
        log.info(">>> [REDIS_SYNC] 잔액 캐시 업데이트 - MemberId: {}, Balance: {}", memberId, balance);
        redisTemplate.opsForHash().put("AUTH:MEMBER:" + memberId, "balance", balance.toPlainString());
    }
}