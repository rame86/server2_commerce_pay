package com.example.payment.service;
// service/PaymentEventListener.java
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.example.payment.config.RabbitMQConfig;
import com.example.payment.dto.request.PaymentRequestDto;

@Service
public class PaymentEventListener {

    // 지정된 큐를 구독하고, JSON 데이터를 DTO로 자동 변환하여 받음
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receivePaymentRequest(PaymentRequestDto requestDto) {
        try {
            System.out.println("결제 요청 수신: " + requestDto.getReservationId());
            // 여기서 실제 결제 로직(PG사 연동, 원장 기록 등) 수행
            
        } catch (Exception e) {
            System.err.println("결제 처리 중 에러 발생: " + e.getMessage());
            // Nack 처리 및 Dead Letter Queue(DLQ)로 보내는 등 예외 처리 필요
        }
    }
}


