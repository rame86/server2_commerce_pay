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

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionHistoryRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션으로 성능 최적화
    public List<WalletDTO> getAllWallets() {
        return walletRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 자체 지갑 조회 로직 (없으면 자동 생성)
    private Wallet getOrCreateWallet(Long memberId) {
        return walletRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    log.info("[WALLET_CREATE] 지갑 자동 생성 - memberId: {}", memberId);
                    Wallet newWallet = Wallet.builder()
                            .memberId(memberId)
                            .balance(BigDecimal.ZERO)
                            .status("ACTIVE")
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    @Override
    @Transactional
    public BigDecimal getBalance(Long memberId) {
        // 지갑 정보가 없으면 지갑 자동 생성 후 잔액 반환
        return getOrCreateWallet(memberId).getBalance();
    }

    @Override
    @Transactional
    public void processPayment(PaymentEventDTO dto) {
        // 지갑찾기 및 없으면 자동 생성
        Wallet wallet = getOrCreateWallet(dto.getMemberId());

        // 활성화된 지갑인지 확인
        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new IllegalStateException("유효하지 않은 지갑 상태입니다.");
        }

        // 지갑의 잔액 가져오기
        BigDecimal currentBalance = wallet.getBalance();

        // 주문금액과 잔액 비교 (Entity 내부 deductBalance 메서드에서 검증 가능)
        if (currentBalance.compareTo(dto.getAmount()) < 0) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        // 지갑 잔액에서 주문금액 뺀후 변수저장 및 업데이트
        // 낙관적 락(Version) 기반 업데이트는 JPA가 커밋 시점에 자동으로 처리함
        wallet.deductBalance(dto.getAmount());
        BigDecimal newBalance = wallet.getBalance();

        // 결제내역 저장
        recordTransaction(
                wallet.getWalletId(),
                dto.getType(),
                dto.getAmount().negate(),
                newBalance,
                dto.getOrderId(),
                dto.getEventTitle() != null ? dto.getEventTitle() : "결제 차감",
                dto.getOriginalPrice(),
                dto.getFee(),
                dto.getShippingFee(),
                dto.getQuantity(),
                dto.getArtistId());
    }

    @Override
    @Transactional
    public void processRefund(PaymentEventDTO dto) {
        // 원본 결제 내역 검증
        TransactionHistory paymentTx = transactionRepository.findTopByReferenceIdAndTransactionType(dto.getOrderId(),
                "PAYMENT");
        if (paymentTx == null) {
            throw new IllegalArgumentException("원본 결제 내역을 찾을 수 없습니다.");
        }

        // 멱등성 보장: 이미 환불된 내역인지 확인
        if (transactionRepository.existsByReferenceIdAndTransactionType(dto.getOrderId(), "REFUND")) {
            log.info("이미 환불 처리된 주문입니다. 주문번호: {}", dto.getOrderId());
            return;
        }

        UUID walletId = paymentTx.getWalletId();

        // DB에서 조회한 금액을 안전하게 확보
        BigDecimal paymentAmount = paymentTx.getAmount();
        log.info("결제된 금액 : " + paymentAmount);

        BigDecimal refundAmount = paymentAmount.abs();
        log.info("환불 요청 금액 : " + refundAmount);

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        // 현재 잔액 조회
        BigDecimal currentBalance = wallet.getBalance();
        log.info("현재 잔액 : " + currentBalance);

        // 금액 환불하기 (Dirty Checking으로 자동 업데이트)
        wallet.addBalance(refundAmount);
        BigDecimal newBalance = wallet.getBalance();
        log.info("환불후 잔액 : " + newBalance);

        // 환불내역 저장
        recordTransaction(
                walletId,
                dto.getType(),
                refundAmount,
                newBalance,
                dto.getOrderId(),
                dto.getEventTitle() != null ? dto.getEventTitle() : "결제 취소 환불",
                dto.getOriginalPrice(),
                dto.getFee(),
                dto.getShippingFee(),
                dto.getQuantity(),
                dto.getArtistId());
    }

    // 트랜잭션 원장 기록 공통 메서드
    private void recordTransaction(UUID walletId, String type, BigDecimal amount, BigDecimal balanceAfter,
            String referenceId, String description, BigDecimal originalAmount, BigDecimal fee,
            BigDecimal shippingFee, Integer quantity, Long artistId) {

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

    // DTO 변환 유틸리티
    private WalletDTO convertToResponseDTO(Wallet wallet) {
        long count = transactionRepository.countByWalletIdAndTransactionType(
            wallet.getWalletId(), "PAYMENT");
        return WalletDTO.builder()
                .walletId(wallet.getWalletId())
                .memberId(wallet.getMemberId())
                .balance(wallet.getBalance())
                .status(wallet.getStatus())
                .version(wallet.getVersion() != null ? wallet.getVersion() : 0)
                .build();
    }

    @Override
    public void updateRedisBalance(Long memberId, BigDecimal balance) {
        redisTemplate.opsForHash().put("AUTH:MEMBER:" + memberId, "balance", balance.toPlainString());
    }

}