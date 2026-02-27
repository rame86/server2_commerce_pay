// src/main/java/com/example/payment/mapper/PaymentMapper.java

package com.example.payment.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.example.payment.dto.response.WalletResponseDTO;

@Mapper
public interface PaymentMapper {
    // 모든 지갑 정보를 조회하는 메서드
    List<WalletResponseDTO> findAllWallets();
}