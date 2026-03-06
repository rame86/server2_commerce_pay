// src/main/java/com/example/payment/service/WalletService.java
package com.example.payment.service;

import java.math.BigDecimal;
import java.util.List;

import com.example.payment.dto.response.WalletResponseDTO;

public interface WalletService {

    // 모든 지갑 정보 조회
    List<WalletResponseDTO> getAllWallets();

    // 회원 ID로 잔액 조회
    BigDecimal getBalance(Long memberId);

    // 결제 처리 (잔액 차감 및 원장 기록)
    void processPayment(Long memberId, String orderId, BigDecimal amount);

    // 환불 처리 (잔액 복구 및 원장 기록)
    void processRefund(String orderId);

    // 잔액 변동시 레디스 업데이트
    void updateRedisBalance(Long memberId, BigDecimal balance);
}