package com.example.payment.service.provider;

import com.example.payment.domain.Charge;
import com.example.payment.dto.response.PaymentReadyResponseDTO;

public interface PaymentProvider {

    // 해당 Provider가 요청된 PG사를 처리할 수 있는지 확인
    boolean supports(String pgProvider);

    // PG사별 결제 준비 API 호출 (payType 파라미터 제거하여 결합도 최소화)
    PaymentReadyResponseDTO ready(Charge charge, Long memberId);

    // PG사별 결제 승인 API 호출
    void approve(Charge charge, String pgToken);

}