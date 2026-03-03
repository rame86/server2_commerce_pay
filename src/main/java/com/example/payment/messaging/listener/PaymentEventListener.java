// service/PaymentEventListener.java
package com.example.payment.messaging.listener;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.request.PaymentRequestDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentEventProducer producer;

    // 지정된 큐를 구독하고, JSON 데이터를 DTO로 자동 변환하여 받음
    // pay.request.queue 바라보기
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receivePaymentRequest(PaymentRequestDTO requestDTO) {
        String orderId = requestDTO.getOrderId();
        // 요청한 서비스가 적어놓은 반송 주소
        String replyKey = requestDTO.getReplyRoutingKey();
        try {
            // 1. 발신자에게 진행 중 상태 알림
            producer.sendStatusUpdate(replyKey, orderId, "PROCESSING", "결제가 진행 중입니다.");
            log.info("결제 로직 시작 - 주문번호: {}, 금액: {}", orderId, requestDTO.getAmount());

            // 2. 실제 결제 로직 수행 (월렛에서 포인트 차감)
            Thread.sleep(5000);
            
            // 3. 완료 시 발신자에게 성공 알림
            producer.sendStatusUpdate(replyKey, orderId, "COMPLETE", "결제가 성공적으로 완료되었습니다.");

        } catch (InterruptedException e) {
            // Thread.sleep에 대한 구체적 예외 처리
            log.error("결제 처리 중 인터럽트 발생: {}", e.getMessage());
            Thread.currentThread().interrupt(); // 인터럽트 상태 복구
            producer.sendStatusUpdate(replyKey, orderId, "FAIL", "시스템 중단으로 인한 결제 실패");
        } catch (AmqpException e) {
            // 메시징 오류나 기타 런타임 예외를 구체적으로 캐치 (멀티 캐치)
            log.error("결제 처리 중 오류 발생: {}", e.getMessage());
            producer.sendStatusUpdate(replyKey, orderId, "FAIL", "결제 실패: " + e.getMessage());
        }
    }
}
