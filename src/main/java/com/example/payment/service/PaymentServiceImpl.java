// src/main/java/com/example/payment/service/PaymentServiceImpl.java
package com.example.payment.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.response.WalletResponseDTO;
import com.example.payment.mapper.PaymentMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;

    @Override
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션으로 성능 최적화
    public List<WalletResponseDTO> getAllWallets() {
      
        return paymentMapper.findAllWallets();
    }

    public WalletResponseDTO getWalletPoints(Long memberId) {
        BigDecimal balance = paymentMapper.getBalanceByMemberId(memberId);
        
        // 지갑 정보가 없으면 잔액 0원 반환 (보안 및 실행 가능성 고려)
        if (balance == null) {
            return new WalletResponseDTO(BigDecimal.ZERO);
        }
        
        return new WalletResponseDTO(balance);
    }
}