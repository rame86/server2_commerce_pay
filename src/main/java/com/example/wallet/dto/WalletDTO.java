// src/main/java/com/example/payment/dto/response/WalletResponseDTO.java
package com.example.wallet.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;

/**
 * 지갑 정보 응답 DTO (Record + Builder)
 */
@Builder
public record WalletDTO(
    UUID walletId,
    Long memberId,
    BigDecimal balance,
    String status,
    Integer version,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}