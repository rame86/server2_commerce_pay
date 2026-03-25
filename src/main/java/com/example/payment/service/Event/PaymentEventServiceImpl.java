//src/main/java/com/example/payment/service/Event/PaymentEventServiceImpl.java
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventServiceImpl implements PaymentEventService {

    private final WalletService walletService;
    private final PaymentEventProducer producer;
    private final SettlementService settlementService;

    @Lazy
    @Autowired
    private PaymentEventServiceImpl self; // 트랜잭션 전파를 위한 자기 참조

    // ──────────────────────────────────────────────────
    // @Transactional 제거: MQ 발송이 트랜잭션 밖에서 실행되야 함.
    // 비즈니스 로직은 executeBusinessLogic(REQUIRES_NEW)에서 별도 트랜잭션으로 격리.
    // ──────────────────────────────────────────────────

    @Override
    public void processPaymentEvent(PaymentEventDTO dto) {
        if (dto.getFee() == null) {
            log.error("수수료 정보가 없습니다. 주문번호: {}", dto.getOrderId());
            return;
        }
        log.info(">>> [PAYMENT] 결제 요청 수신 데이터: {}", dto);

        executeWithStatusUpdate(dto, "COMPLETE", "결제 성공", () -> {
            walletService.processPayment(dto);
            settlementService.processSettlement(dto);
            return null;
        });
    }

    @Override
    public void processRefundEvent(PaymentEventDTO dto) {
        log.info(">>> [REFUNDED] 결제 요청 수신 데이터: {}", dto);

        executeWithStatusUpdate(dto, "REFUNDED", "환불 성공", () -> {
            walletService.processRefund(dto);
            settlementService.processSettlement(dto);
            return null;
        });
    }

    @Override
    public void processDonationEvent(PaymentEventDTO dto) {
        dto.setFee(BigDecimal.valueOf(20));
        dto.setOriginalPrice(dto.getAmount());

        log.info(">>> [DONATION] 결제 요청 수신 데이터: {}", dto);
        executeWithStatusUpdate(dto, "COMPLETE", "후원 성공", () -> {
            walletService.processPayment(dto);
            settlementService.processSettlement(dto);
            return null;
        });
    }

    @Override
    public void processArtistSettlementRequest(PaymentEventDTO dto) {
        // 비즈니스 로직
    }

    @Override
    @Transactional
    public void processArtistWalletCreate(PaymentEventDTO dto) {
        log.info(">>> [ARTIST_APPROVE] 요청 정보 MemberID: {}, Name: {}", dto.getMemberId(), dto.getArtistName());
    }

    /**
     * [이벤트 처리 공통 템플릿]
     * 비즈니스 로직은 REQUIRES_NEW 트랜잭션으로 격리 실행.
     * MQ 발송(성공/실패)은 트랜잭션 바깥에서 실행되므로 UnexpectedRollbackException 방지.
     */
    private void executeWithStatusUpdate(PaymentEventDTO dto, String successStatus, String successMsg,
            java.util.concurrent.Callable<Void> businessLogic) {
        String replyKey = dto.getReplyRoutingKey();
        String orderId = dto.getOrderId();
        String type = dto.getType();

        try {
            // 비즈니스 로직을 별도 트랜잭션(REQUIRES_NEW)으로 실행
            // 예외 발생 시 이 트랜잭션만 롤백되고, 아래 catch는 트랜잭션 바깥에서 실행됨
            self.executeBusinessLogic(businessLogic);

            // 성공 응답 발송 (트랜잭션 커밋 완료 후 실행)
            producer.sendDataResponse(replyKey, orderId, successStatus, successMsg, type, null);
            log.info("[{}] 처리 완료 - 주문번호: {}", type, orderId);

        } catch (Exception e) {
            // 트랜잭션은 이미 롤백됨. 여기서 MQ 발송은 트랜잭션 외부이므로 안전.
            log.error("[{}] 처리 실패 - 주문번호: {}, 사유: {}", dto.getType(), dto.getOrderId(), e.getMessage());
            producer.sendDataResponse(replyKey, orderId, "FAIL", e.getMessage(), "ERROR", null);
        }
    }

    /**
     * 비즈니스 로직 전용 트랜잭션 (REQUIRES_NEW).
     * 예외 발생 시 이 트랜잭션만 롤백되고, 호출자(executeWithStatusUpdate)의 catch로 예외가 전달됨.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeBusinessLogic(java.util.concurrent.Callable<Void> logic) throws Exception {
        logic.call();
    }
}