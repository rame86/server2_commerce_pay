//src/main/java/com/example/payment/service/PaymentService.java
package com.example.payment.service;

import java.util.List;

import com.example.payment.dto.response.WalletResponseDTO;

public interface PaymentService {
    List<WalletResponseDTO> getAllWallets();

    public Long getWalletPoints(Long memberId);
}