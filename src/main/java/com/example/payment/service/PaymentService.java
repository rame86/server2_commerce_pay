//src/main/java/com/example/payment/service/PaymentService.java
package com.example.payment.service;

import java.util.UUID;

import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;

public interface PaymentService {
    // 기존 결제 충전 로직
    ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request);
    void approvePayment(UUID chargeId, String pgToken, String memberId);

    // [Refactored] 통합 이벤트 핸들러
    void handleEvent(PaymentEventDTO dto);
    
    // 개별 이벤트 처리 로직
    void processPaymentEvent(PaymentEventDTO dto);
    void processRefundEvent(PaymentEventDTO dto);
    void processDonationEvent(PaymentEventDTO dto);
}