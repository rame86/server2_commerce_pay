package com.example.payment.dto.response;

import java.util.List;

import lombok.Builder;

@Builder
public record UserDetailPaymentResponseDTO(
    int totalPurchases,
    long pointBalance,
    List<PurchaseHistoryDTO> purchaseHistory,
    List<PointHistoryDTO> pointHistory
) {}
