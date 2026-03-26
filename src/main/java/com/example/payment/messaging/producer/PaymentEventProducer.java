// src/main/java/com/example/payment/messaging/producer/PaymentEventProducer.java
package com.example.payment.messaging.producer;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.event.PaymentResponseDTO;

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
     * [데이터 응답 메시지 발송]
     * 결제 성공/실패 여부와 필요한 페이로드를 특정 서비스로 전송함.
     * * @param <T>              페이로드 데이터 타입 (제네릭)
     * @param targetRoutingKey 메시지가 도달할 목적지 큐의 라우팅 키 (예: "shop.payment.success")
     * @param orderId          주문 고유 번호
     * @param status           처리 상태 (SUCCESS, FAILED 등)
     * @param message          처리 결과 메시지
     * @param type             이벤트 유형 식별자
     * @param payload          추가 데이터 객체 (지갑 잔액, 결제 정보 등)
     */
    public <T> void sendDataResponse(String targetRoutingKey, String orderId, String status, String message, String type, T payload) {
        log.info(">>> [EVENT_PUBLISH] 메시지 발행 시도 - Target: {}, OrderId: {}, Status: {}, Type: {}", 
                targetRoutingKey, orderId, status, type);

        // 공통 응답 DTO 생성: 제네릭을 사용하여 다양한 데이터 구조를 유연하게 수용함
        PaymentResponseDTO<T> responseDTO = new PaymentResponseDTO<>(orderId, status, message, type, payload);
        
        try {
            // 발송 데이터 로깅: 트래킹을 위해 발송 직전의 상세 데이터를 남김
            log.info(">>> [EVENT_PUBLISH] 페이로드 데이터 로깅 - OrderId: {}, Payload: {}", orderId, payload);

            /**
             * RabbitMQ 메시지 발송
             * 1. EXCHANGE_NAME: 메시지를 받을 교환기
             * 2. targetRoutingKey: 교환기가 메시지를 배달할 경로 키
             * 3. responseDTO: 메시지 바디 (JSON으로 직렬화되어 전송됨)
             */
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, targetRoutingKey, responseDTO);
            
            log.info(">>> [EVENT_PUBLISH] RabbitMQ 메시지 발송 완료 - Target: {}, OrderId: {}, Status: {}", 
                    targetRoutingKey, orderId, status);
            
        } catch (AmqpException e) {
            // 메시지 브로커(RabbitMQ) 연결 실패 또는 메시지 거부 시 예외 처리
            log.error(">>> [EVENT_PUBLISH] 메시지 발행 실패 - OrderId: {}, Error: {}", orderId, e.getMessage());
            // 필요 시 재시도 로직이나 DB 별도 기록 로직 추가 검토 필요
        }
    }
}