//src/main/java/com/example/payment/service/PaymentService.java
package com.example.payment.service;

import java.util.UUID;

import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;
import com.example.payment.dto.response.PaymentHistoryResponseDTO;

public interface PaymentService {
    // 포인트 충전 준비
    ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request, String token);

    // 실제 포인트 충전 결제
    void approvePayment(UUID chargeId, String pgToken, String memberId);

    // Refactored 통합 이벤트 핸들러
    void handleEvent(PaymentEventDTO dto);
    
    // 개별 이벤트 처리 로직
    void processPaymentEvent(PaymentEventDTO dto);
    void processRefundEvent(PaymentEventDTO dto);
    void processDonationEvent(PaymentEventDTO dto);
    
    // 지갑이 없으면 자동 생성 후 저장
    PaymentHistoryResponseDTO getPaymentHistory(Long memberId);
}