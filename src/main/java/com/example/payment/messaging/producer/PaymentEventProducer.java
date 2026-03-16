// src/main/java/com/example/payment/messaging/producer/PaymentEventProducer.java
package com.example.payment.messaging.producer;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.event.PaymentResponseDTO;

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
    public <T> void sendDataResponse(String targetRoutingKey, String orderId, String status, String message, String type, T payload) {
        // 제네릭 타입 추론을 위해 다이아몬드 연산자(<>) 명시
        PaymentResponseDTO<T> responseDTO = new PaymentResponseDTO<>(orderId, status, message, type, payload);
        
        try {
            // [추가] 발송 직전 Payload 상세 데이터 로깅
            log.info("발송 예정 페이로드 상세 데이터 - 주문번호: {}, 데이터: {}", orderId, payload);

            // targetRoutingKey에 따라 Shop 또는 Res 큐로 동적 발송됨
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, targetRoutingKey, responseDTO);
            log.info("상태 업데이트 발송 완료 - 목적지: {}, 주문번호: {}, 상태: {}", targetRoutingKey, orderId, status);
        } catch (AmqpException e) {
            log.error("메시지 발송 실패 - 주문번호: {}, 에러: {}", orderId, e.getMessage());
        }
    }
}