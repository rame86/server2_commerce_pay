//src/main/java/com/example/payment/service/PaymentService.java
package com.example.payment.service;

import java.util.UUID;

import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;

public interface PaymentService {
    public ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request);

    public void approvePayment(UUID chargeId, String pgToken, String memberId);

}