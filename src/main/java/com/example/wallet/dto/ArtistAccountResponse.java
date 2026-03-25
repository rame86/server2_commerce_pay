package com.example.wallet.dto;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * 아티스트 계좌 잔액 정보 응답 DTO
 */
@Builder
public record ArtistAccountResponse(
    BigDecimal totalBalance,
    BigDecimal withdrawableBalance
) {
}
