// src/main/java/com/example/payment/messaging/listener/PaymentEventListener.java
package com.example.payment.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.service.Event.PaymentEventService;
import com.example.payment.service.Event.SettlementEventService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [결제/정산 이벤트 리스너]
 * RabbitMQ에서 메시지를 소비(Consume)하여 유형별로 서비스 레이어에 전달함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentEventService paymentService;
    private final SettlementEventService settlementEventService;

    /**
     * [메시지 수신 및 라우팅]
     * RabbitMQ 큐에서 PaymentEventDTO를 수신하여 타입에 따라 분기 처리함.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveMessage(PaymentEventDTO dto) {
        log.info(">>> [MQ_RECEIVE] 메시지 수신 및 라우팅 시작 - Type: {}, OrderId: {}, ReplyKey: {}", 
                dto.getType(), dto.getOrderId(), dto.getReplyRoutingKey());

        // 수량 정보 누락 시 기본값 1로 보정
        if (dto.getQuantity() == null) {
            log.debug(">>> [MQ_FIX] 수량 정보 누락으로 인한 기본값(1) 설정 - OrderId: {}", dto.getOrderId());
            dto.setQuantity(1);
        }

        // [이벤트 타입별 분기] 직접적인 비즈니스 로직은 호출 서비스로 위임
        switch (dto.getType()) {
            case "PAYMENT" -> paymentService.processPaymentEvent(dto);      // 결제 승인
            case "REFUND" -> paymentService.processRefundEvent(dto);         // 환불 처리
            case "DONATION" -> paymentService.processDonationEvent(dto);    // 후원 처리

            // 관리자 및 통계 관련 라우팅
            case "ADMIN" -> handleAdminRequest(dto);                        // 관리자 기능 분기 호출
            case "ADMIN_SETTLEMENT" -> settlementEventService.processAdminSettlement(dto);

            // 아티스트 정산 및 지갑 관련 라우팅
            case "ARTIST_SETTLEMENT_REQUEST" -> paymentService.processArtistSettlementRequest(dto);
            case "ARTIST_APPROVE" -> paymentService.processArtistWalletCreate(dto);

            default -> log.error(">>> [MQ_ERROR] 지원하지 않는 메시지 타입: {}", dto.getType());
        }
    }

    /**
     * [관리자 요청 상세 라우팅]
     * 'ADMIN' 타입 메시지의 orderId 필드를 액션(Action) 구분자로 사용하여 상세 기능 수행.
     */
    private void handleAdminRequest(PaymentEventDTO dto) {
        String action = dto.getOrderId(); // 관리자 요청에서는 orderId를 기능 구분값으로 활용
        log.info(">>> [ADMIN_ROUTING] 관리자 상세 요청 라우팅 시작 - Action: {}, OrderId: {}", action, dto.getOrderId());
        
        if (action == null) {
            log.error(">>> [ADMIN_ERROR] 상세 기능 구분(action) 데이터가 누락되었습니다.");
            return;
        }

        switch (action) {
            case "GETALL" -> settlementEventService.processAdminGetAll(dto);         // 전체 내역 조회
            case "ARTIST" -> settlementEventService.processAdminArtistDetail(dto);   // 아티스트별 상세
            case "SUMMARY" -> settlementEventService.processAdminSummary(dto);       // 정산 요약 통계
            case "USER_DETAIL" -> settlementEventService.processAdminUserDetail(dto); // 유저 결제 상세
            default -> log.error(">>> [ADMIN_ERROR] 정의되지 않은 관리자 액션: {}", action);
        }
    }
}