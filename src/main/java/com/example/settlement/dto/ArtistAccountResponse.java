// src/main/java/com/example/wallet/dto/ArtistAccountResponse.java
package com.example.settlement.dto;

import java.math.BigDecimal;

import lombok.Builder;

/**
 * [아티스트 계좌 잔액 정보 응답 DTO]
 * 아티스트가 보유한 총 누적 수익과 현재 출금 가능한 잔액 정보를 담고 있음.
 * Record 타입을 사용하여 불변성을 유지하고 Getter, 생성자 등을 자동으로 생성함.
 */
@Builder
public record ArtistAccountResponse(
    /** 총 누적 수익 (지금까지 정산 완료된 모든 금액의 합계) */
    BigDecimal totalBalance,

    /** 실제 출금 가능 잔액 (정산 완료 건 중 아직 출금하지 않고 보유 중인 금액) */
    BigDecimal withdrawableBalance
) {
}