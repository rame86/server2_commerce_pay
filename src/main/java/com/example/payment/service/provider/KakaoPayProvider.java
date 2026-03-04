// src/main/java/com/example/payment/service/provider/KakaoPayProvider.java

package com.example.payment.service.provider;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.payment.config.KakaoPayProperties;
import com.example.payment.domain.Charge;
import com.example.payment.dto.response.PaymentReadyResponseDTO;

@Component
public class KakaoPayProvider implements PaymentProvider {

    private final KakaoPayProperties properties;
    private final RestClient restClient;

    public KakaoPayProvider(KakaoPayProperties properties) {
        this.properties = properties;

        // Spring Boot 3.2+ RestClient: 공통 헤더 및 Base URL 초기화
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 카카오페이 최신 API 규격에 맞춘 인증 헤더
                .defaultHeader(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + properties.secretKey())
                .build();
    }

    // pg사가 KAKAO_PAY일때만 사용되도록 검증
    @Override
    public boolean supports(String pgProvider) {
        return "KAKAO_PAY".equals(pgProvider);
    }

    // Ready
    @Override
    public PaymentReadyResponseDTO ready(Charge charge) {
        // Map 대신 내부 Record를 사용하여 타입 안정성 확보
        KakaoPayReadyRequest request = new KakaoPayReadyRequest(
                properties.cid(),
                charge.getChargeId().toString(),
                charge.getWalletId().toString(),
                "포인트 충전",
                1,
                charge.getAmount().intValue(),
                0,
                0,
                properties.approvalUrl(),
                properties.cancelUrl(),
                properties.failUrl());

        try {
            KakaoPayReadyResponse response = restClient.post()
                    .uri("/online/v1/payment/ready")
                    .body(request)
                    .retrieve()
                    .body(KakaoPayReadyResponse.class);

            // 예외 방어 로직 강화
            if (response == null || response.tid() == null) {
                throw new IllegalStateException("카카오페이 결제 준비 응답 오류: TID 누락");
            }

            return PaymentReadyResponseDTO.builder()
                    .chargeId(charge.getChargeId())
                    .payType("KAKAOPAY") // 파라미터 대신 구현체에서 직접 할당
                    .nextRedirectUrl(response.next_redirect_pc_url())
                    .providerTid(response.tid())
                    .build();

        } catch (RestClientException e) {
            // 통신 장애 시 구체적인 예외 전환 (추후 Custom Exception 사용 권장)
            throw new RuntimeException("카카오페이 API 네트워크 통신 실패: " + e.getMessage(), e);
        }
    }
    
    // Approve
    @Override
    public void approve(Charge charge, String pgToken) {
        KakaoPayApproveRequest request = new KakaoPayApproveRequest(
                properties.cid(),
                charge.getPgTransactionId(), // Ready 단계에서 받아 DB에 저장해둔 TID
                charge.getChargeId().toString(),
                charge.getWalletId().toString(),
                pgToken);

        try {
            KakaoPayApproveResponse response = restClient.post()
                    .uri("/online/v1/payment/approve")
                    .body(request)
                    .retrieve()
                    // [Self-Review] 외부 API 장애 및 잘못된 토큰 응답에 대한 예외 처리 방어
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                        throw new IllegalStateException("카카오페이 승인 거절: " + res.getStatusCode());
                    })
                    .body(KakaoPayApproveResponse.class);

            if (response == null || response.tid() == null) {
                throw new IllegalStateException("비정상적인 승인 응답 수신");
            }

        } catch (Exception e) {
            throw new RuntimeException("카카오페이 승인 API 네트워크 통신 실패: " + e.getMessage(), e);
        }
    }
    // --- 내부 DTO 역할을 하는 Record ---

    // Ready 영역
    private record KakaoPayReadyRequest(
            String cid,
            String partner_order_id,
            String partner_user_id,
            String item_name,
            Integer quantity,
            Integer total_amount,
            Integer vat_amount,
            Integer tax_free_amount,
            String approval_url,
            String cancel_url,
            String fail_url) {
    }

    private record KakaoPayReadyResponse(
            String tid,
            String next_redirect_app_url,
            String next_redirect_mobile_url,
            String next_redirect_pc_url,
            String android_app_scheme,
            String ios_app_scheme,
            String created_at) {
    }

    // Approve 영역
    private record KakaoPayApproveRequest(
            String cid,
            String tid,
            String partner_order_id,
            String partner_user_id,
            String pg_token) {
    }

    private record KakaoPayApproveResponse(
            String aid,
            String tid,
            String cid,
            String partner_order_id,
            String partner_user_id,
            String payment_method_type,
            String approved_at) {
    }
}