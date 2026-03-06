// service/PaymentEventListener.java
package com.example.payment.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.example.config.RabbitMQConfig;
import com.example.payment.dto.request.PaymentRequestDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;
import com.example.payment.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentEventProducer producer;
    private final WalletService walletService;

    // 지정된 큐를 구독하고, JSON 데이터를 DTO로 자동 변환하여 받음
    // pay.request.queue 바라보기
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveMessage(PaymentRequestDTO requestDTO) {
        String type = requestDTO.getType();

        // 메시지 타입에 따른 메소드 라우팅
        switch (type) {
            case "PAYMENT" -> handlePayment(requestDTO); // 결제요청 -> 결제 완료시 반환값 = "COMPLETE"
            case "REFUND" -> handleRefund(requestDTO); // 환불요청 -> 환불 완료시 반환값 = "REFUNDED"
            default -> log.error("알 수 없는 메시지 타입: {}, 주문번호: {}", type, requestDTO.getOrderId());
        }
    }

    // 결제 요청 처리 logic
    private void handlePayment(PaymentRequestDTO requestDTO) {
        String orderId = requestDTO.getOrderId();
        String replyKey = requestDTO.getReplyRoutingKey();
        String type = requestDTO.getType();

        try {
            producer.sendStatusUpdate(replyKey, orderId, "PROCESSING", "결제가 진행 중입니다.",type);
            log.info("[PAYMENT] 결제 시작 - 주문번호: {}", orderId);

            // 비즈니스 로직 시뮬레이션
            walletService.processPayment(requestDTO.getMemberId(), requestDTO.getOrderId(), requestDTO.getAmount());
            Thread.sleep(3000);

            producer.sendStatusUpdate(replyKey, orderId, "COMPLETE", "결제가 성공적으로 완료되었습니다.", type);
        } catch (InterruptedException e) {
            handleError(replyKey, orderId, "시스템 중단으로 인한 결제 실패", e);
        } catch (Exception e) {
            handleError(replyKey, orderId, "결제 실패: " + e.getMessage(), e);
        }
    }

    // 환불 요청 처리 logic
    private void handleRefund(PaymentRequestDTO requestDTO) {
        String orderId = requestDTO.getOrderId();
        String replyKey = requestDTO.getReplyRoutingKey();
        String type = requestDTO.getType();

        try {
            log.info("[REFUND] 환불 시작 - 주문번호: {}", orderId);

            // 환불 로직 수행 (예: 월렛 포인트 복구)
            walletService.processRefund(orderId);
            Thread.sleep(3000);

            // 요구사항에 따른 "REFUNDED" 상태 전송
            producer.sendStatusUpdate(replyKey, orderId, "REFUNDED", "환불 처리가 완료되었습니다.", type);
        } catch (InterruptedException e) {
            handleError(replyKey, orderId, "시스템 중단으로 인한 환불 실패", e);
        } catch (Exception e) {
            handleError(replyKey, orderId, "환불 실패: " + e.getMessage(), e);
        }
    }

    // 공통 예외 처리 및 실패 메시지 전송
    private void handleError(String replyKey, String orderId, String errorMsg, Exception e) {
        log.error("처리 중 오류 발생 - 주문번호: {}, 사유: {}", orderId, e.getMessage());
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        producer.sendStatusUpdate(replyKey, orderId, "FAIL", errorMsg, "ERROR");
    }

}
