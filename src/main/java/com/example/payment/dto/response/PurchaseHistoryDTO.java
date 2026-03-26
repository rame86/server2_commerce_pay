// src/main/java/com/example/payment/dto/response/PurchaseHistoryDTO.java
package com.example.payment.dto.response;

import lombok.Builder;

/**
 * [상품 구매 이력 DTO]
 * 사용자가 지출한 결제 건 중 '상품/서비스 구매'에 특화된 정보를 전달함.
 * 마이페이지의 구매 목록이나 주문 상세 화면에서 활용됨.
 */
@Builder
public record PurchaseHistoryDTO(
    /** 결제가 완료된 일시 (예: "2026-03-18 15:30") */
    String purchasedAt,  

    /** 구매한 상품 또는 서비스의 명칭 (예: "아티스트 공식 응원봉") */
    String itemName,     

    /** 해당 상품 구매에 소요된 총 결제 금액 */
    long amount,         

    /** 결제 상태 (예: COMPLETED: 결제완료, REFUNDED: 환불됨, CANCELLED: 주문취소) */
    String status,

    /** 구매한 상품의 수량 (예: 1) */
    Integer quantity     
) {}

/*
데이터 예시:
{
  "purchasedAt": "2026-03-26 15:45",
  "itemName": "World Tour 2026 티켓 (R석)",
  "amount": 154000,
  "status": "COMPLETED",
  "quantity": 2
}
*/