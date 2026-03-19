//src/main/java/com/example/payment/service/PaymentService.java
package com.example.payment.service.Event;

import com.example.payment.dto.event.PaymentEventDTO;

public interface PaymentEventService {
    // 통합 이벤트 핸들러 (MQ 메시지 소비용)
    void handleEvent(PaymentEventDTO dto);
    
    // 개별 이벤트 처리 로직
    void processPaymentEvent(PaymentEventDTO dto);
    void processRefundEvent(PaymentEventDTO dto);
    void processDonationEvent(PaymentEventDTO dto);
    void processArtistSettlementRequest(PaymentEventDTO dto);
    void processArtistWalletCreate(PaymentEventDTO dto);
}