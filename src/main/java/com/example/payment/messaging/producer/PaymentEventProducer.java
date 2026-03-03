// src/main/java/com/example/payment/messaging/producer/PaymentEventProducer.java
package com.example.payment.messaging.producer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.response.PaymentResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final RabbitTemplate rabbitTemplate;
    
    // Exchange: MSA 공용 분배기 유지
    private static final String EXCHANGE_NAME = RabbitMQConfig.EXCHANGE_NAME;

    /**
     * 상태 업데이트 발송
     * targetRoutingKey를 받아서 요청한 곳으로 정확히 되돌려줌
     */
    public void sendStatusUpdate(String targetRoutingKey, String orderId, String status, String message) {
        PaymentResponseDTO responseDTO = new PaymentResponseDTO(orderId, status, message);
        
        try {
            // targetRoutingKey에 따라 Shop 또는 Res 큐로 동적 발송됨
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, targetRoutingKey, responseDTO);
            log.info("상태 업데이트 발송 완료 - 목적지: {}, 주문번호: {}, 상태: {}", targetRoutingKey, orderId, status);
        } catch (Exception e) {
            log.error("메시지 발송 실패 - 주문번호: {}, 에러: {}", orderId, e.getMessage());
        }
    }
}