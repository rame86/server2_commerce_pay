// src/main/java/com/example/payment/dto/response/DashboardSummaryDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;


public record DashboardSummaryDTO(
    BigDecimal totalGrossAmount,      // 총 거래액
    BigDecimal totalPlatformFee,      // 플랫폼 수수료
    BigDecimal totalExpectedAmount,   // 정산 예정액
    BigDecimal totalSettledAmount     // 정산 완료액
) {}