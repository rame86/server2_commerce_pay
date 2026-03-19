package com.example.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPaymentSummaryDTO {
    private Long memberId;      // 유저 식별자
    private Integer purchaseCount; // 총 구매 횟수
    private Long balance;       // 현재 잔액
    private Integer version;    // 데이터 버전 (낙관적 락용) ㅡㅡ🚔
}
