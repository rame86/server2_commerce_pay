//src/main/java/com/example/payment/service/PaymentService.java
package com.example.payment.service;

import java.util.UUID;

import com.example.payment.dto.request.PaymentRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;

public interface PaymentService {
    public ChargeReadyResponseDTO readyPayment(Long memberId, PaymentRequestDTO request);

    public void approvePayment(UUID chargeId, String pgToken, String memberId);

}