package com.example.payment.dto.response;

import lombok.Builder;

@Builder
public record PointHistoryDTO(
    String processedAt,   // 처리 일시
    String type,          // 구분 (PAYMENT, CHARGE, REFUND 등)
    long amount,          // 변동 금액 (사용은 -, 충전은 +)
    String description,   // 상세 내용 (예: "포인트 충전", "상품 결제")
    long balanceAfter     // 거래 후 잔액 (이게 있어야 관리자가 안심해! ㅡㅡ🚔)
) {}
