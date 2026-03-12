// src/main/java/com/example/payment/dto/response/WalletResponseDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;

/**
 * 지갑 정보 응답 DTO (Record + Builder)
 */
@Builder
public record WalletResponseDTO(
    UUID walletId,
    Long memberId,
    BigDecimal balance,
    String status,
    Integer version,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}