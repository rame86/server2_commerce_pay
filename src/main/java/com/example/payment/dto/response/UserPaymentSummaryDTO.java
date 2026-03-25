package com.example.payment.dto.response;

import lombok.Builder;

/*
Long memberId,         // 유저 식별자
Integer purchaseCount; // 총 구매 횟수
Long balance;          // 현재 잔액
Integer version;       // 데이터 버전 (낙관적 락용
 */

@Builder
public record UserPaymentSummaryDTO(
        Long memberId,
        Integer purchaseCount,
        Long balance,
        Integer version) {}
