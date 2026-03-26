// src/main/java/com/example/payment/service/Event/PaymentEventServiceImpl.java
package com.example.payment.service.Event;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;
import com.example.settlement.service.SettlementService;
import com.example.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [결제 이벤트 처리 서비스 구현체]
 * 트랜잭션과 외부 메시징 발송을 분리하여 안정적인 비동기 처리를 수행함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventServiceImpl implements PaymentEventService {

    private final WalletService walletService;
    private final PaymentEventProducer producer;
    private final SettlementService settlementService;

    /** * [자기 참조(Self-Injection)]
     * 내부 메서드 호출 시 프록시 객체를 통해 @Transactional(REQUIRES_NEW)이 
     * 정상적으로 작동하도록 함. (@Lazy로 순환 참조 방지)
     */
    @Lazy
    @Autowired
    private PaymentEventServiceImpl self;

    @Override
    public void processPaymentEvent(PaymentEventDTO dto) {
        log.info(">>> [PAYMENT] 결제 처리 시작: {}", dto.getOrderId());
        if (dto.getFee() == null) {
            log.error("수수료 정보가 없습니다. 주문번호: {}", dto.getOrderId());
            return;
        }

        executeWithStatusUpdate(dto, "COMPLETE", "결제 성공", () -> {
            walletService.processPayment(dto); // 지갑 잔액 차감 및 이력 생성
            settlementService.processSettlement(dto); // 정산 데이터 생성
            return null;
        });
    }

    @Override
    public void processRefundEvent(PaymentEventDTO dto) {
        log.info(">>> [REFUNDED] 환불 처리 시작: {}", dto.getOrderId());

        executeWithStatusUpdate(dto, "REFUNDED", "환불 성공", () -> {
            walletService.processRefund(dto); // 지갑 잔액 복구
            settlementService.processSettlement(dto); // 정산 원장 취소 처리
            return null;
        });
    }

    @Override
    public void processDonationEvent(PaymentEventDTO dto) {
        log.info(">>> [DONATION] 후원 처리 시작: {}", dto.getOrderId());
        // 후원 전용 정책 적용 (수수료 고정 등)
        dto.setFee(BigDecimal.valueOf(20));
        dto.setOriginalPrice(dto.getAmount());

        executeWithStatusUpdate(dto, "COMPLETE", "후원 성공", () -> {
            walletService.processPayment(dto);
            settlementService.processSettlement(dto);
            return null;
        });
    }

    @Override
    public void processArtistSettlementRequest(PaymentEventDTO dto) {
        log.info(">>> [SETTLEMENT] 아티스트 정산 요청 처리 시작: {}", dto.getArtistId());
        // 아티스트 정산 요청 비즈니스 로직 구현 예정
    }

    @Override
    @Transactional
    public void processArtistWalletCreate(PaymentEventDTO dto) {
        log.info(">>> [ARTIST_APPROVE] 아티스트 지갑 생성: {}", dto.getArtistName());
        // 아티스트 계좌(ArtistAccount) 초기화 로직
    }

    /**
     * [이벤트 처리 공통 템플릿 메서드]
     * 1. 비즈니스 로직을 별도의 독립 트랜잭션(REQUIRES_NEW)으로 실행.
     * 2. DB 트랜잭션이 완료(커밋/롤백)된 후 MQ 응답을 발송함.
     * 3. 이를 통해 MQ 발송 실패가 DB 롤백을 유발하거나, 롤백 후 잘못된 성공 메시지가 나가는 것을 방지.
     */
    private void executeWithStatusUpdate(PaymentEventDTO dto, String successStatus, String successMsg,
            java.util.concurrent.Callable<Void> businessLogic) {
        log.info(">>> [TEMPLATE] 이벤트 처리 템플릿 시작 - Type: {}, OrderId: {}", dto.getType(), dto.getOrderId());
        
        String replyKey = dto.getReplyRoutingKey();
        String orderId = dto.getOrderId();
        String type = dto.getType();

        try {
            // [독립 트랜잭션 실행] 실패 시 이 메서드 내부만 롤백됨
            self.executeBusinessLogic(businessLogic);

            // [성공 시 MQ 발송] 트랜잭션 바깥이므로 안전하게 실행
            producer.sendDataResponse(replyKey, orderId, successStatus, successMsg, type, null);
            log.info("[{}] 처리 완료 - 주문번호: {}", type, orderId);

        } catch (Exception e) {
            // [실패 시 MQ 발송] 트랜잭션은 이미 롤백된 상태에서 에러 응답 전송
            log.error("[{}] 처리 실패 - 주문번호: {}, 사유: {}", dto.getType(), dto.getOrderId(), e.getMessage());
            producer.sendDataResponse(replyKey, orderId, "FAIL", e.getMessage(), "ERROR", null);
        }
    }

    /**
     * [비즈니스 로직 격리 실행]
     * Propagation.REQUIRES_NEW: 호출 측과 무관하게 새로운 트랜잭션을 시작함.
     * 로직 실패 시 해당 트랜잭션만 즉시 롤백하여 데이터 일관성을 유지함.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeBusinessLogic(java.util.concurrent.Callable<Void> logic) throws Exception {
        log.info(">>> [BUSINESS_LOGIC] 독립 트랜잭션 비즈니스 로직 실행 시작");
        logic.call();
    }
}