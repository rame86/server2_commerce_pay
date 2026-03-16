// src/main/java/com/example/payment/dto/response/AdminDashboardResponseDTO.java
package com.example.payment.dto.response;

import java.util.List;

public record AdminDashboardResponseDTO(
    DashboardSummaryDTO summary,
    List<ArtistSettlementRowDTO> artistSettlements
) {}