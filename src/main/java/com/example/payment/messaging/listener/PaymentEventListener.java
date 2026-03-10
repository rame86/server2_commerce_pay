// service/PaymentEventListener.java
package com.example.payment.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentService paymentService;

    /**
     * 지정된 큐를 구독하고 서비스 계층으로 처리를 위임
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveMessage(PaymentEventDTO requestDTO) {
        log.info("[MQ 수신] 타입: {}, 주문번호: {}", requestDTO.getType(), requestDTO.getOrderId());
        
        // 서비스 계층의 통합 이벤트 핸들러 호출
        paymentService.handleEvent(requestDTO);
    }
}