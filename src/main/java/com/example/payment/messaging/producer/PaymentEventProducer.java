// src/main/java/com/example/payment/messaging/producer/PaymentEventProducer.java
package com.example.payment.messaging.producer;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.event.PaymentEventResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [결제 이벤트 발행자]
 * 결제 처리 결과를 RabbitMQ Exchange로 발행하여 타 마이크로서비스(MSA)에 전파함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final RabbitTemplate rabbitTemplate;
    // MSA 전체에서 공유되는 Topic Exchange 이름
    private static final String EXCHANGE_NAME = RabbitMQConfig.EXCHANGE_NAME;

    /**
     * [메시지 발송]
     * 완성된 DTO를 받아 RabbitMQ로 발송.
     * 
     * @param targetRoutingKey 메시지가 도달할 목적지 큐의 라우팅 키 (예: "shop.payment.success")
     * @param responseDTO      발송할 데이터 객체 (결과 상태, 메시지, 페이로드 등)
     */
    public <T> void sendMessage(String targetRoutingKey, PaymentEventResponseDTO<T> responseDTO) {
        log.info(">>> [EVENT_PUBLISH] 메시지 발행 시도 - Target: {}, OrderId: {}",
                targetRoutingKey, responseDTO.orderId());

        try {
            // 발송 데이터 로깅: 트래킹을 위해 발송 직전의 상세 데이터를 남김
            log.info(">>> [EVENT_PUBLISH] 페이로드 데이터 로깅 - OrderId: {}, Payload: {}", responseDTO.orderId(),
                    responseDTO.payload());

            /**
             * RabbitMQ 메시지 발송
             * 1. EXCHANGE_NAME: 메시지를 받을 교환기
             * 2. targetRoutingKey: 교환기가 메시지를 배달할 경로 키
             * 3. responseDTO: 메시지 바디 (jackson2JsonMessageConverter()를 통해 JSON으로 직렬화되어 전송됨)
             */
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, targetRoutingKey, responseDTO);

            log.info(">>> [EVENT_PUBLISH] 발송 완료 - Status: {}", responseDTO.status());

        } catch (AmqpException e) {
            // 메시지 브로커(RabbitMQ) 연결 실패 또는 메시지 거부 시 예외 처리
            log.error(">>> [EVENT_PUBLISH] 발행 실패 - OrderId: {}, Error: {}", responseDTO.orderId(), e.getMessage());
        }
    }
}