// src/main/java/com/example/payment/service/Event/PaymentBusinessServiceImpl.java
package com.example.payment.service.Event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.payment.dto.service.TransactionDTO;
import com.example.settlement.service.SettlementLedgerService;
import com.example.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [결제 비즈니스 로직 서비스 구현체]
 * 지갑 처리와 정산 기록을 하나의 물리적 트랜잭션으로 묶어 원자성을 보장함.
 * AOP 프록시 문제를 피하고 REQUIRES_NEW 전파 속성을 보장하기 위해 분리된 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentBusinessServiceImpl implements PaymentBusinessService {

    private final WalletService walletService;
    private final SettlementLedgerService settlementService;

    /**
     * [결제/후원 처리 트랜잭션]
     * 지갑 포인트 차감과 파트너 정산 데이터 생성을 하나의 트랜잭션으로 처리.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionDTO executePaymentLogic(PaymentEventRequestDTO dto) {
        log.info(">>> [BIZ_PROCESS] 결제 로직 트랜잭션 시작 - OrderId: {}", dto.getOrderId());
        
        // 1. 지갑 잔액 차감 및 결제 원장 생성
        TransactionDTO payload = walletService.processPayment(dto); 
        
        // 2. 파트너 정산 원장 데이터 생성
        settlementService.processSettlement(dto); 
        
        return payload;
    }

    /**
     * [환불 처리 트랜잭션]
     * 지갑 포인트 복구와 정산 데이터 취소 처리를 하나의 트랜잭션으로 처리.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionDTO executeRefundLogic(PaymentEventRequestDTO dto) {
        log.info(">>> [BIZ_PROCESS] 환불 로직 트랜잭션 시작 - OrderId: {}", dto.getOrderId());
        
        // 1. 지갑 잔액 복구 및 환불 원장 생성
        TransactionDTO payload = walletService.processRefund(dto); 
        
        // 2. 파트너 정산 원장 취소 처리
        settlementService.processSettlement(dto); 
        
        return payload;
    }
}