// src/main/java/com/example/payment/dto/response/PaymentHistoryResponseDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;

/**
 * [사용자 지갑 및 거래 이력 응답 DTO]
 * 현재 사용 가능한 잔액 정보와 상세 거래 히스토리를 클라이언트에 전달함.
 */
@Builder
public record PaymentHistoryResponseDTO(
    /** * [핵심] 사용자의 현재 실시간 지갑 잔액.
     * 결제 가능 여부를 판단하는 기준값이 됨.
     */
    BigDecimal currentBalance,

    /** * [이력] 사용자가 수행한 모든 자산 변동 리스트.
     * 충전(CHARGE), 결제(PAYMENT), 환불(REFUND), 후원(DONATION) 내역을 포함함.
     */
    List<TransactionDTO> transactions
) {}

/*
데이터 예시:
{
  "currentBalance": 10000.00,
  "transactions": [
    {
      "transactionId": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
      "walletId": "f47ac10b-58cc-4372-a567-0e02b2c3d4e5",
      "transactionType": "REFUND",
      "amount": 5000.00,
      "originalPrice": 5000.00,
      "fee": 0.00,
      "shippingFee": 0.00,
      "quantity": 1,
      "artistId": 77,
      "balanceAfter": 10000.00,
      "referenceId": "ORD-20260326-999",
      "description": "공연 티켓 환불 완료",
      "createdAt": "2026-03-26T15:30:00+09:00"
    },
    {
      "transactionId": "b2c3d4e5-f6a7-4b6c-9d8e-1f2a3b4c5d6e",
      "walletId": "f47ac10b-58cc-4372-a567-0e02b2c3d4e5",
      "transactionType": "PAYMENT",
      "amount": -5000.00,
      "originalPrice": 5000.00,
      "fee": 500.00,
      "shippingFee": 0.00,
      "quantity": 1,
      "artistId": 77,
      "balanceAfter": 5000.00,
      "referenceId": "ORD-20260326-999",
      "description": "공연 티켓 예매",
      "createdAt": "2026-03-26T14:00:00+09:00"
    },
    {
      "transactionId": "c3d4e5f6-a7b8-4c7d-8e9f-2a3b4c5d6e7f",
      "walletId": "f47ac10b-58cc-4372-a567-0e02b2c3d4e5",
      "transactionType": "CHARGE",
      "amount": 10000.00,
      "originalPrice": 0.00,
      "fee": 0.00,
      "shippingFee": 0.00,
      "quantity": 0,
      "artistId": null,
      "balanceAfter": 10000.00,
      "referenceId": "TID-KAKAO-12345678",
      "description": "카카오페이 포인트 충전",
      "createdAt": "2026-03-26T13:00:00+09:00"
    }
  ]
}
*/