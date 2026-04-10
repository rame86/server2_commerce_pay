// src/main/java/com/example/payment/dto/response/UserDetailPaymentResponseDTO.java
package com.example.admin.dto.admin.response;

import java.util.List;

import lombok.Builder;

/**
 * [사용자 결제 상세 대시보드 DTO]
 * 유저의 총 구매 횟수, 잔액, 그리고 모든 활동 이력을 하나로 묶어 전달함.
 */
@Builder
public record UserDetailPaymentResponseDTO(
    /** 사용자가 지금까지 완료한 총 구매 건수 */
    int totalPurchases,

    /** 사용자가 현재 보유 중인 실시간 포인트 잔액 */
    long pointBalance,

    /** * [구매 이력] 상품명, 금액, 일시 등 실제 물품/서비스 구매 리스트.
     * '무엇을 샀는지'에 대한 정보 중심.
     */
    List<PurchaseHistoryDTO> purchaseHistory,

    /** * [포인트 이력] 충전, 사용, 환불 등 지갑의 자산 흐름 리스트.
     * '돈이 어떻게 움직였는지'에 대한 증빙 중심.
     */
    List<PointHistoryDTO> pointHistory
) {}

/*
데이터 예시:
{
  "totalPurchases": 12,
  "pointBalance": 25000,
  "purchaseHistory": [
    {
      "purchasedAt": "2026-03-26 14:00",
      "itemName": "아티스트 시즌 그리팅",
      "amount": 45000,
      "status": "COMPLETED",
      "quantity": 1
    }
  ],
  "pointHistory": [
    {
      "processedAt": "2026-03-26 14:00",
      "type": "PAYMENT",
      "amount": -45000,
      "description": "상품 결제: 아티스트 시즌 그리팅",
      "balanceAfter": 25000
    },
    {
      "processedAt": "2026-03-26 10:00",
      "type": "CHARGE",
      "amount": 50000,
      "description": "포인트 충전",
      "balanceAfter": 70000
    }
  ]
}
*/