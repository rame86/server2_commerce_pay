// src/main/java/com/example/payment/service/Event/PaymentEventServiceImpl.java
package com.example.payment.service.Event;

import java.math.BigDecimal;
import java.util.concurrent.Callable;

import org.springframework.stereotype.Service;

import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.payment.dto.event.PaymentEventResponseDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [결제 이벤트 제어 (Saga 패턴)]
 * 메시지 큐(RabbitMQ)로부터 수신된 이벤트를 기반으로 결제/환불/후원제어.
 * 실질적인 비즈니스 트랜잭션(지갑 차감, 정산 기록)은 PaymentBusinessService로 위임.
 * 이벤트 상태 추적(PENDING -> COMPLETE/FAIL)과 보상 트랜잭션 유도를 위한 메시지 발송 전담.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventServiceImpl implements PaymentEventService {

    private final PaymentEventProducer producer;
    private final TransactionEventService transactionEventService;
    private final PaymentBusinessService businessService;

    /**
     * [결제 이벤트 처리]
     * 결제 요청 시 데이터 정합성을 검증하고 결제 비즈니스 로직 호출.
     */
    @Override
    public void processPaymentEvent(PaymentEventRequestDTO dto) {
        log.info(">>> [PAYMENT_EVENT] 결제 요청 처리: {}", dto.getOrderId());

        // 수수료 정보가 누락된 경우 기본값(0)으로 보정하여 하위 로직의 NPE 방지
        if (dto.getFee() == null) {
            log.warn(">>> [PAYMENT_EVENT:FEE_NULL] 수수료 정보 누락 - OrderId: {}",
                    dto.getOrderId());

            dto.setFee(BigDecimal.ZERO);
        }

        // 템플릿 콜백을 통해 독립 트랜잭션 로직 전달 및 상태 업데이트 수행
        executeWithStatusUpdate(dto, "COMPLETE", "결제 성공",
                () -> businessService.executePaymentLogic(dto));
    }

    /**
     * [환불 이벤트 처리]
     * 결제 취소 요청 시 기존 거래 원장을 기반으로 환불 로직 호출.
     */
    @Override
    public void processRefundEvent(PaymentEventRequestDTO dto) {
        log.info(">>> [REFUND_EVENT] 환불 요청 처리: {}", dto.getOrderId());

        // 템플릿 콜백을 통해 환불 독립 트랜잭션 로직 전달
        executeWithStatusUpdate(dto, "REFUNDED", "환불 성공",
                () -> businessService.executeRefundLogic(dto));
    }

    /**
     * [후원 이벤트 처리]
     * 팬 커뮤니티 플랫폼 정책에 맞추어 후원 시 고정 수수료를 적용한 결제 로직을 호출.
     */
    @Override
    public void processDonationEvent(PaymentEventRequestDTO dto) {
        log.info(">>> [DONATION_EVENT] 후원 요청 처리: {}", dto.getOrderId());

        // 커뮤니티 정책: 수수료 20% 고정 및 원결제금액 세팅
        dto.setFee(BigDecimal.valueOf(20));
        dto.setOriginalPrice(dto.getAmount());

        // 후원도 일반 결제와 동일한 트랜잭션 로직(차감 및 정산)을 재사용
        executeWithStatusUpdate(dto, "COMPLETE", "후원 성공",
                () -> businessService.executePaymentLogic(dto));
    }

    /**
     * [이벤트 처리 공통 템플릿 (Saga 상태 관리 핵심)]
     * 분산 시스템의 데이터 정합성을 위해 비즈니스 흐름을 다음 4단계를 거쳐 제어:
     * 1. PENDING 기록: 로직 시작 전 DB에 기록을 남김 (유실 방지).
     * 2. 로직 실행: 비즈니스 서비스를 통해 완전 새로운 독립 트랜잭션(REQUIRES_NEW)으로 격리 실행.
     * 3. 결과 반영: 성공 시 COMPLETE, 실패 시 FAIL로 상태 업데이트.
     * 4. MQ 발송: 처리 결과에 따라 주문 서비스로 완료 또는 보상 트랜잭션 유도 메시지 발송.
     */
    private <T> void executeWithStatusUpdate(PaymentEventRequestDTO dto, String successStatus,
            String successMsg, Callable<T> businessLogic) {

        String type = dto.getType();
        String orderId = dto.getOrderId();
        String replyKey = dto.getReplyRoutingKey();

        try {
            // STEP 1: 이벤트 수신 즉시 초기 상태 기록 (무조건 커밋하여 추적 가능하게 함)
            transactionEventService.createPendingEvent(dto);

            // STEP 2: 실제 비즈니스 로직 실행 (PaymentBusinessService에서 물리적으로 분리된 트랜잭션)
            T payload = businessLogic.call();
            
            // STEP 3: 비즈니스 트랜잭션 정상 종료 시 해당 이벤트를 완료 상태로 업데이트
            transactionEventService.updateEventStatus(orderId, successStatus);

            // STEP 4: 최종 성공 데이터(payload)를 담아 응답 큐로 메시지 발행
            PaymentEventResponseDTO<T> responseDTO = new PaymentEventResponseDTO<>(
                    orderId, successStatus, successMsg, type, payload);
            producer.sendMessage(replyKey, responseDTO);

            log.info(">>> [EVENT:SUCCESS] 처리 완료 - Type: {}, OrderId: {}",
                    type, orderId);

        } catch (Exception e) {
            log.error(">>> [EVENT:FAIL] 처리 오류 발생 - OrderId: {}, 사유: {}",
                    orderId, e.getMessage());

            // STEP 3-E: 비즈니스 로직 예외 발생 시, 해당 트랜잭션은 롤백되더라도 이벤트 상태는 FAIL로 기록
            transactionEventService.updateEventStatus(orderId, "FAIL");

            // STEP 4-E: 실패 메시지 발송 (주문 서비스 등 호출 측에서 보상 트랜잭션을 수행할 수 있도록 알림)
            PaymentEventResponseDTO<Void> errorDTO = new PaymentEventResponseDTO<>(
                    orderId, "FAIL", e.getMessage(), "ERROR", null);
            producer.sendMessage(replyKey, errorDTO);
        }
    }
}