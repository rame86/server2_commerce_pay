// service/PaymentEventListener.java
package com.example.payment.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.service.Event.PaymentEventService;
import com.example.payment.service.Event.SettlementEventService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentEventService paymentService;
    private final SettlementEventService settlementEventService; // 리스너가 라우팅을 위해 두 서비스를 모두 가짐

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveMessage(PaymentEventDTO dto) {
        log.info("[MQ 수신] 타입: {}, 주문번호: {}", dto.getType(), dto.getOrderId());

        if (dto.getQuantity() == null) {
            dto.setQuantity(1);
        }

        // 리스너에서 직접 타입에 따라 알맞은 서비스로 라우팅
        switch (dto.getType()) {
            case "PAYMENT" -> paymentService.processPaymentEvent(dto);
            case "REFUND" -> paymentService.processRefundEvent(dto);
            case "DONATION" -> paymentService.processDonationEvent(dto);
            
            // 관리자 조회 관련 라우팅
            case "ADMIN" -> handleAdminRequest(dto);
            case "ADMIN_SETTLEMENT" -> settlementEventService.processAdminSettlement(dto);
            
            // 아티스트 관련 라우팅
            case "ARTIST_SETTLEMENT_REQUEST" -> paymentService.processArtistSettlementRequest(dto);
            case "ARTIST_APPROVE" -> paymentService.processArtistWalletCreate(dto);
            
            default -> log.error("알 수 없는 메시지 타입: {}", dto.getType());
        }
    }

    private void handleAdminRequest(PaymentEventDTO dto) {
        String action = dto.getOrderId();
        if (action == null) {
            log.error("ADMIN 요청에 기능 구분(orderId)이 없습니다.");
            return;
        }
        switch (action) {
            case "GETALL" -> settlementEventService.processAdminGetAll(dto);
            case "ARTIST" -> settlementEventService.processAdminArtistDetail(dto);
            case "SUMMARY" -> settlementEventService.processAdminSummary(dto);
            case "USER_DETAIL" -> settlementEventService.processAdminUserDetail(dto);
            default -> log.error("알 수 없는 ADMIN 상세 기능: {}", action);
        }
    }
}