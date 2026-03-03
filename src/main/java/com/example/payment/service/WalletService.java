// src/main/java/com/example/payment/service/WalletService.java
package com.example.payment.service;

import java.util.List;

import com.example.payment.dto.response.WalletResponseDTO;

public interface WalletService {

    public List<WalletResponseDTO> getAllWallets();

    public Long getBalance(Long memberId);

}
