// src/main/java/com/example/wallet/dto/WalletDTO.java
package com.example.wallet.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;

/**
 * [지갑 정보 응답 DTO]
 * 지갑 엔티티의 데이터를 외부(API 응답, 타 서비스 전송 등)로 노출할 때 사용함.
 * Record 타입을 사용하여 불변(Immutable) 상태를 유지하며, 빌더 패턴으로 유연한 생성을 지원함.
 */
@Builder
public record WalletDTO(
    /** 지갑 고유 식별자 (UUID) */
    UUID walletId,

    /** Core 서비스와 연동되는 회원 식별자 (Member ID) */
    Long memberId,

    /** 현재 보유 잔액 (BigDecimal을 사용한 정밀한 금액 정보) */
    BigDecimal balance,

    /** 지갑 상태 (예: ACTIVE, INACTIVE, BLOCKED) */
    String status,

    /** 데이터 정합성 확인을 위한 버전 정보 (Optimistic Lock Version) */
    Integer version,

    /** 지갑 최초 생성 일시 */
    OffsetDateTime createdAt,

    /** 지갑 최종 수정 일시 */
    OffsetDateTime updatedAt
) {
}