// src/main/java/com/example/payment/service/WalletServiceImpl.java
package com.example.payment.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.TransactionHistory;
import com.example.payment.domain.Wallet;
import com.example.payment.dto.response.WalletResponseDTO;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.payment.repository.WalletRepository;

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
    public List<WalletResponseDTO> getAllWallets() {
        return walletRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getBalance(Long memberId) {
        // 지갑 정보가 없으면 잔액 0원 반환 (보안 및 실행 가능성 고려)
        return walletRepository.findByMemberId(memberId)
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public void processPayment(Long memberId, String orderId, BigDecimal amount) {
        // 지갑찾기
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalStateException("지갑이 없습니다."));

        // 활성화된 지갑인지 확인
        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new IllegalStateException("유효하지 않은 지갑 상태입니다.");
        }

        // 지갑의 잔액 가져오기
        BigDecimal currentBalance = wallet.getBalance();

        // 주문금액과 잔액 비교 (Entity 내부 deductBalance 메서드에서 검증 가능)
        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        // 지갑 잔액에서 주문금액 뺀후 변수저장 및 업데이트
        // 낙관적 락(Version) 기반 업데이트는 JPA가 커밋 시점에 자동으로 처리함
        wallet.deductBalance(amount);
        BigDecimal newBalance = wallet.getBalance();

        // 결제내역 저장
        recordTransaction(wallet.getWalletId(), "PAYMENT", amount.negate(), newBalance, orderId, "결제 차감");
    }

    @Override
    @Transactional
    public void processRefund(String orderId) {
        // 원본 결제 내역 검증
        TransactionHistory paymentTx = transactionRepository.findTopByReferenceIdAndTransactionType(orderId, "PAYMENT");
        if (paymentTx == null) {
            throw new IllegalArgumentException("원본 결제 내역을 찾을 수 없습니다.");
        }

        // 멱등성 보장: 이미 환불된 내역인지 확인
        if (transactionRepository.existsByReferenceIdAndTransactionType(orderId, "REFUND")) {
            log.info("이미 환불 처리된 주문입니다. 주문번호: {}", orderId);
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
        recordTransaction(walletId, "REFUND", refundAmount, newBalance, orderId, "결제 취소 환불");
    }

    // 트랜잭션 원장 기록 공통 메서드
    private void recordTransaction(UUID walletId, String type, BigDecimal amount, BigDecimal balanceAfter, String referenceId,
            String description) {
        TransactionHistory tx = TransactionHistory.builder()
                .walletId(walletId)
                .transactionType(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .referenceId(referenceId)
                .description(description)
                .build();

        transactionRepository.save(tx);
    }

    // DTO 변환 유틸리티
    private WalletResponseDTO convertToResponseDTO(Wallet wallet) {
        return WalletResponseDTO.builder()
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