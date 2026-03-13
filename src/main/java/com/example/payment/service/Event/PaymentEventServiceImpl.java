//src/main/java/com/example/payment/service/PaymentServiceImpl.java
package com.example.payment.service.Event;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;
import com.example.payment.service.settlement.SettlementService;
import com.example.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventServiceImpl implements PaymentEventService {

    // Charge 관련 Repository 의존성 모두 제거
    private final WalletService walletService;
    private final PaymentEventProducer producer;
    private final SettlementService settlementService;

    @Lazy
    @Autowired
    private PaymentEventServiceImpl self;

    /**
     * [메시지 이벤트 핸들러]
     * MQ를 통해 수신된 메시지의 타입에 따라 적절한 비즈니스 로직으로 라우팅함
     */
    @Override
    public void handleEvent(PaymentEventDTO dto) {
        if(dto.getQuantity() == null){
            dto.setQuantity(1);
        }
        switch (dto.getType()) {
            case "PAYMENT" -> self.processPaymentEvent(dto);
            case "REFUND" -> self.processRefundEvent(dto);
            case "DONATION" -> self.processDonationEvent(dto);
            case "SETTLEMENT" -> self.processSettlement(dto); 

            default -> log.error("알 수 없는 메시지 타입: {}", dto.getType());
        }
    }

    @Override
    @Transactional
    public void processPaymentEvent(PaymentEventDTO dto) {
        if(dto.getFee() == null) {
            log.error("수수료 정보가 없습니다. 주문번호: {}", dto.getOrderId());
            return;
        }
        
        log.info(">>> [PAYMENT] 결제 요청 수신 데이터: {}", dto);

        executeWithStatusUpdate(dto, "COMPLETE", "결제 성공", () -> {
            // 1. 유저 지갑에서 금액 차감 및 결제 원장 기록
            walletService.processPayment(dto);
            // 2. 아티스트 정산 데이터 기록 및 잔액 업데이트
            settlementService.processSettlement(dto);
            return null;
        });
    }

    @Override
    @Transactional
    public void processRefundEvent(PaymentEventDTO dto) {

        log.info(">>> [REFUNDED] 결제 요청 수신 데이터: {}", dto);

        executeWithStatusUpdate(dto, "REFUNDED", "환불 성공", () -> {
            // 1. 유저 지갑에서 금액 차감 및 결제 원장 기록
            walletService.processRefund(dto);
            // 2. 아티스트 정산 데이터 기록 및 잔액 업데이트
            settlementService.processSettlement(dto);
            return null;
        });
    }

    @Override
    @Transactional
    public void processDonationEvent(PaymentEventDTO dto) {
        
        dto.setFee(BigDecimal.valueOf(20));
        dto.setOriginalPrice(dto.getAmount());

        log.info(">>> [DONATION] 결제 요청 수신 데이터: {}", dto);
        executeWithStatusUpdate(dto, "COMPLETE", "후원 성공", () -> {
             // 1. 유저 지갑에서 금액 차감 및 결제 원장 기록
            walletService.processPayment(dto);
             // 2. 아티스트 정산 데이터 기록 및 잔액 업데이트
            settlementService.processSettlement(dto);
            return null;
        });
    }

    @Override
    public void processSettlement(PaymentEventDTO dto) {

    };

    /**
     * [이벤트 처리 공통 템플릿]
     * 비즈니스 로직 전후로 MQ 상태 업데이트(PROCESSING -> SUCCESS/FAIL)를 처리함
     */
    private void executeWithStatusUpdate(PaymentEventDTO dto, String successStatus, String successMsg,
            java.util.concurrent.Callable<Void> businessLogic) {
        String replyKey = dto.getReplyRoutingKey();
        String orderId = dto.getOrderId();
        String type = dto.getType();

        try {
            producer.sendDataResponse(replyKey, orderId, "PROCESSING", "처리 중입니다.", type, null);

            businessLogic.call();

            producer.sendDataResponse(replyKey, orderId, successStatus, successMsg, type, null);
            log.info("[{}] 처리 완료 - 주문번호: {}", type, orderId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleEventError(dto, "시스템 중단", e);
        } catch (Exception e) {
            handleEventError(dto, e.getMessage(), e);
        }
    }

    private void handleEventError(PaymentEventDTO dto, String errorMsg, Exception e) {
        log.error("[{}] 처리 실패 - 주문번호: {}, 사유: {}", dto.getType(), dto.getOrderId(), errorMsg);
        producer.sendDataResponse(dto.getReplyRoutingKey(), dto.getOrderId(), "FAIL", errorMsg, "ERROR", null);
    }

}