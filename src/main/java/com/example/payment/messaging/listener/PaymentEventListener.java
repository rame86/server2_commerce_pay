// service/PaymentEventListener.java
package com.example.payment.messaging.listener;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.request.PaymentRequestDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaymentEventListener {

    // 지정된 큐를 구독하고, JSON 데이터를 DTO로 자동 변환하여 받음
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receivePaymentRequest(PaymentRequestDTO requestDTO) {
       try {
            log.info("결제 요청 수신 완료 - 예약번호: {}, 금액: {}", 
                     requestDTO.getReservationId(), requestDTO.getAmount());
            
            // 실제 결제 로직 (PG사 연동, 원장 테이블 insert 등) 수행
            
        } catch (Exception e) {
            log.error("결제 처리 중 에러 발생: {}", e.getMessage());
            // 에러 발생 시 DLQ(Dead Letter Queue)로 메시지를 옮기거나, 재시도(Retry) 처리 로직 필요
        }
    }
}


