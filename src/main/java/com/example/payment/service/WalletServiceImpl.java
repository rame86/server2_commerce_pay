// src/main/java/com/example/payment/service/WalletServiceImpl.java
package com.example.payment.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.response.WalletResponseDTO;
import com.example.payment.mapper.WalletMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletMapper walletMapper;

    @Override
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션으로 성능 최적화
    public List<WalletResponseDTO> getAllWallets() {
        return walletMapper.findAllWallets();
    }

    @Override
    public Long getBalance(Long memberId) {
        Long balance = walletMapper.getBalanceByMemberId(memberId);
        // 지갑 정보가 없으면 잔액 0원 반환 (보안 및 실행 가능성 고려)
        if (balance == null) {
            return balance;
        }
        return balance;
    }

    @Override
    @Transactional
    public void processPayment(Long memberId, String orderId, Long amount) {
        WalletResponseDTO wallet = walletMapper.findWalletByMemberId(memberId);
        if (wallet == null || !"ACTIVE".equals(wallet.getStatus())) {
            throw new IllegalStateException("유효하지 않은 지갑 상태입니다.");
        }

        Long currentBalance = wallet.getBalance();

        if (currentBalance < amount) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        Long newBalance = currentBalance - amount;

        // 낙관적 락(Version) 기반 업데이트
        int updatedRows = walletMapper.updateWalletBalance(wallet.getWalletId(), newBalance, wallet.getVersion());
        if (updatedRows == 0) {
            throw new IllegalStateException("결제 처리 중 충돌이 발생했습니다. 다시 시도해주세요.");
        }

        recordTransaction(wallet.getWalletId(), "PAYMENT", -amount, newBalance, orderId, "결제 차감");
    }

    @Override
    @Transactional
    public void processRefund(String orderId) {
        // 원본 결제 내역 검증
        Map<String, Object> paymentTx = walletMapper.findTransactionByReferenceAndType(orderId, "PAYMENT");
        if (paymentTx == null) {
            throw new IllegalArgumentException("원본 결제 내역을 찾을 수 없습니다.");
        }

        // 멱등성 보장: 이미 환불된 내역인지 확인
        Map<String, Object> refundTx = walletMapper.findTransactionByReferenceAndType(orderId, "REFUND");
        if (refundTx != null) {
            log.info("이미 환불 처리된 주문입니다. 주문번호: {}", orderId);
            return;
        }

        String walletId = (String) paymentTx.get("wallet_id");
        // DB에서 조회한 금액(Object)을 안전하게 Long으로 변환
        Long paymentAmount = ((Number) paymentTx.get("amount")).longValue();
        Long refundAmount = Math.abs(paymentAmount);

        WalletResponseDTO wallet = walletMapper.findWalletById(walletId);
        Long currentBalance = wallet.getBalance();
        Long newBalance = currentBalance + refundAmount;

        // 낙관적 락(Version) 기반 복구
        int updatedRows = walletMapper.updateWalletBalance(walletId, newBalance, wallet.getVersion());
        if (updatedRows == 0) {
            throw new IllegalStateException("환불 처리 중 충돌이 발생했습니다. 다시 시도해주세요.");
        }

        recordTransaction(walletId, "REFUND", refundAmount, newBalance, orderId, "결제 취소 환불");
    }

    // 트랜잭션 원장 기록 공통 메서드
    private void recordTransaction(String walletId, String type, Long amount, Long balanceAfter, String referenceId,
            String description) {
        Map<String, Object> txData = new HashMap<>();
        txData.put("walletId", walletId);
        txData.put("transactionType", type);
        txData.put("amount", amount);
        txData.put("balanceAfter", balanceAfter);
        txData.put("referenceId", referenceId);
        txData.put("description", description);

        walletMapper.insertTransaction(txData);
    }

}
