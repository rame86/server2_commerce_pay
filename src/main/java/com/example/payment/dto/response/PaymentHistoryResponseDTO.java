// src/main/java/com/example/payment/dto/response/PaymentHistoryResponseDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentHistoryResponseDTO {
    private BigDecimal currentBalance;
    private List<TransactionDTO> transactions;

    @Getter
    @Builder
    public static class TransactionDTO {
        private String transactionType;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
        private String description;
        private OffsetDateTime createdAt;
    }
}