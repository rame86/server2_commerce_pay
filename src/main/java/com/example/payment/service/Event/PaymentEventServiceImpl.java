//src/main/java/com/example/payment/service/Event/PaymentEventServiceImpl.java
package com.example.payment.service.Event;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;
import com.example.payment.service.settlement.SettlementService;
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

    // 기존에 있던 SettlementEventService 의존성 및 Lazy 처리 제거됨 (결합도 완화)

    @Override
    @Transactional
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
    @Transactional
    public void processRefundEvent(PaymentEventDTO dto) {
        log.info(">>> [REFUNDED] 결제 요청 수신 데이터: {}", dto);

        executeWithStatusUpdate(dto, "REFUNDED", "환불 성공", () -> {
            walletService.processRefund(dto);
            settlementService.processSettlement(dto);
            return null;
        });
    }

    @Override
    @Transactional
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
        // 이건 굳이 답장(Reply) 안 보내도 되는 단순 알림 이벤트의 좋은 예시
        log.info(">>> [ARTIST_APPROVE] 요청 정보 MemberID: {}, Name: {}", dto.getMemberId(), dto.getArtistName());
    }

    /**
     * [이벤트 처리 공통 템플릿]
     * PROCESSING 발송 로직은 제거해도 좋다면 제거하는 것을 추천해.
     */
    private void executeWithStatusUpdate(PaymentEventDTO dto, String successStatus, String successMsg,
            java.util.concurrent.Callable<Void> businessLogic) {
        String replyKey = dto.getReplyRoutingKey();
        String orderId = dto.getOrderId();
        String type = dto.getType();

        try {
            // 주석 처리. 굳이 PROCESSING을 보낼 필요가 없다면 지움.
            // producer.sendDataResponse(replyKey, orderId, "PROCESSING", "처리 중입니다.", type,
            // null);

            businessLogic.call();

            // 코어 서버가 응답을 기다리므로 성공/실패 여부는 보내야 함
            producer.sendDataResponse(replyKey, orderId, successStatus, successMsg, type, null);
            log.info("[{}] 처리 완료 - 주문번호: {}", type, orderId);

        } catch (Exception e) {
            log.error("[{}] 처리 실패 - 주문번호: {}, 사유: {}", dto.getType(), dto.getOrderId(), e.getMessage());
            producer.sendDataResponse(replyKey, orderId, "FAIL", e.getMessage(), "ERROR", null);
        }
    }
}