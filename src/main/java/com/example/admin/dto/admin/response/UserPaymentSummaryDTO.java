// src/main/java/com/example/payment/dto/response/UserPaymentSummaryDTO.java
package com.example.admin.dto.admin.response;

import lombok.Builder;

/**
 * [유저 결제 요약 정보 DTO]
 * 관리자 페이지나 유저 프로필 상단에서 사용되는 핵심 결제 지표 스냅샷.
 * 낙관적 락(Optimistic Lock)을 위한 버전 정보를 포함함.
 */
@Builder
public record UserPaymentSummaryDTO(
        /** 유저 고유 식별자 (PK) */
        Long memberId,

        /** 유저가 지금까지 수행한 총 구매 횟수 */
        Integer purchaseCount,

        /** * [핵심] 현재 보유 중인 지갑 잔액.
         * 결제 가능 여부를 판단하는 실시간 기준값.
         */
        Long balance,

        /** * [동시성 제어] 데이터 버전 번호.
         * 낙관적 락(Optimistic Locking) 구현 시 사용하며, 
         * 데이터 수정 시 버전 일치 여부를 확인하여 부정 결제나 중복 업데이트를 방지함.
         */
        Integer version
) {
}

/*
데이터 예시:
{
  "memberId": 12345,
  "purchaseCount": 42,
  "balance": 58000,
  "version": 7
}
*/