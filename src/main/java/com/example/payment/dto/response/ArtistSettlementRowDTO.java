// src/main/java/com/example/payment/dto/response/ArtistSettlementRowDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ArtistSettlementRowDTO(
    Long artistId,
    String artistName,           
    BigDecimal grossAmount,
    BigDecimal feeAmount,
    BigDecimal netAmount,
    String status,
    OffsetDateTime lastTransactionDate
) {}