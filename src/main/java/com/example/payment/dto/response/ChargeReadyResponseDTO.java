// src/main/java/com/example/payment/dto/request/PaymentReadyResponseDTO.java
package com.example.payment.dto.response;

import java.util.UUID;

import lombok.Builder;

@Builder
public record ChargeReadyResponseDTO(
                UUID chargeId, // 서비스 결제 고유 번호
                String payType, // 결제 수단
                String nextRedirectUrl, // 결제 페이지 리다이렉트 URL
                String providerTid // PG사 트랜잭션 ID
) {
}