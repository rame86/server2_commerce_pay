package com.example.payment.messaging.producer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.payment.dto.event.PaymentEventDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final RabbitTemplate rabbitTemplate;
    
    // 환경변수나 상수로 관리되는 Exchange 및 Routing Key
    private static final String EXCHANGE_NAME = "payment.exchange";
    private static final String ROUTING_KEY = "payment.process";

    /**
     * 결제 완료 이벤트를 RabbitMQ로 발송
     * @param eventDto 발송할 데이터 객체 (RabbitMQ 설정에 따라 JSON으로 자동 직렬화됨)
     */
    public void sendPaymentCompleteEvent(PaymentEventDTO eventDTO) {
        try {
            // convertAndSend: 객체를 메시지로 변환하여 지정된 Exchange와 Routing Key로 발송
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, eventDTO);
            log.info("결제 이벤트 발송 성공: {}", eventDTO.getPaymentId());
        } catch (Exception e) {
            // 발송 실패 시 비즈니스 로직 전체가 롤백되는 것을 방지하거나,
            // 재시도(Retry)/데드레터(DLQ) 처리를 위한 예외 핸들링 필요
            log.error("결제 이벤트 발송 실패: {}", eventDTO.getPaymentId(), e);
            // 필요에 따라 커스텀 예외를 던져 보상 트랜잭션(Saga 패턴 등)을 트리거할 수 있음
        }
    }
}