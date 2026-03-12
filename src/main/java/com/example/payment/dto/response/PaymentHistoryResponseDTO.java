// src/main/java/com/example/payment/dto/request/PaymentHistoryResponseDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;

/**
 * 결제 이력 응답 DTO
 */
@Builder
public record PaymentHistoryResponseDTO(
    BigDecimal currentBalance,
    List<TransactionDTO> transactions
) {}