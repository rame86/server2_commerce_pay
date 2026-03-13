package com.example.payment.service.charge;

import java.util.UUID;

import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;
import com.example.payment.dto.response.PaymentHistoryResponseDTO;

public interface ChargeService {

    // 지갑이 없으면 자동 생성 후 저장
    PaymentHistoryResponseDTO getPaymentHistory(Long memberId);

    // 포인트 충전 준비
    ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request, String token);

    // 실제 포인트 충전 결제
    void approvePayment(UUID chargeId, String pgToken, String memberId);

}
