package com.example.payment.dto.response;

import lombok.Builder;

@Builder
public record PurchaseHistoryDTO(
    String purchasedAt,  // 결제 일시 (예: "2026-03-18 15:30")
    String itemName,     // 상품명 (예: "아티스트 공식 응원봉")
    long amount,         // 결제 금액 (예: 35000)
    String status,
    Integer quantity     // 구매 수량 (예: 1)
) {}
