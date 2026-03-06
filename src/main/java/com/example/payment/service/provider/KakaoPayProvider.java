// src/main/java/com/example/payment/service/provider/KakaoPayProvider.java

package com.example.payment.service.provider;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.config.KakaoPayProperties;
import com.example.payment.domain.Charge;
import com.example.payment.dto.response.ChargeReadyResponseDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class KakaoPayProvider implements PaymentProvider {

    private final KakaoPayProperties properties;
    private final RestClient restClient;

    public KakaoPayProvider(KakaoPayProperties properties) {
        this.properties = properties;

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 카카오페이 최신 API는 Secret Key를 기반으로 인증함
                .defaultHeader(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + properties.secretKey())
                .build();
    }

    @Override
    public boolean supports(String pgProvider) {
        return "KAKAO_PAY".equals(pgProvider);
    }

    /**
     * [PAYMENT_READY] 결제 준비 단계
     * 사용자가 결제 수단을 선택하고 카카오페이 결제 화면으로 넘어가기 전 TID 발급 및 URL 획득
     */
    @Override
    public ChargeReadyResponseDTO ready(Charge charge, Long memberId) {
        log.info("[PAYMENT_READY] 결제 준비 요청 시작 - ChargeID: {}, Amount: {}", charge.getChargeId(), charge.getAmount());

        String approvalUrlWithId = properties.approvalUrl() + "?chargeId=" + charge.getChargeId() + "&memberId=" + memberId;
        log.info("[PAYMENT_READY] request주소 : " +approvalUrlWithId.toString());

        KakaoPayReadyRequest request = new KakaoPayReadyRequest(
                properties.cid(),
                charge.getChargeId().toString(),
                charge.getWalletId().toString(),
                "포인트 충전",
                1,
                charge.getAmount().intValue(),
                0,
                0,
                approvalUrlWithId,
                properties.cancelUrl(),
                properties.failUrl());

        try {
            KakaoPayReadyResponse response = restClient.post()
                    .uri("/online/v1/payment/ready")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        log.error("[PAYMENT_READY] API 에러 발생 - Status: {}", res.getStatusCode());
                        throw new IllegalStateException("카카오페이 준비 API 호출 실패");
                    })
                    .body(KakaoPayReadyResponse.class);

            if (response == null || response.tid() == null) {
                throw new IllegalStateException("카카오페이 결제 준비 응답 오류: TID 누락");
            }

            log.info("[PAYMENT_READY] 결제 준비 완료 - TID: {}", response.tid());

            return ChargeReadyResponseDTO.builder()
                    .chargeId(charge.getChargeId())
                    .payType("KAKAOPAY")
                    .nextRedirectUrl(response.next_redirect_pc_url())
                    .providerTid(response.tid())
                    .build();

        } catch (RestClientException e) {
            log.error("[PAYMENT_READY] 네트워크 통신 장애 - Message: {}", e.getMessage());
            throw new RuntimeException("카카오페이 API 네트워크 통신 실패", e);
        }
    }

    /**
     * [PAYMENT_APPROVE] 결제 승인 단계
     * 사용자가 인증을 완료한 후 전달받은 pg_token을 사용하여 최종 결제 확정
     */
    @Override
    public void approve(Charge charge, String pgToken) {
        log.info("[PAYMENT_APPROVE] 결제 승인 요청 시작 - ChargeID: {}, TID: {}", charge.getChargeId(),
                charge.getPgTransactionId());

        KakaoPayApproveRequest request = new KakaoPayApproveRequest(
                properties.cid(),
                charge.getPgTransactionId(),
                charge.getChargeId().toString(),
                charge.getWalletId().toString(),
                pgToken);

        try {
            KakaoPayApproveResponse response = restClient.post()
                    .uri("/online/v1/payment/approve")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        // 카카오페이가 보낸 실제 에러 응답 바디 읽기
                        String errorBody = new String(res.getBody().readAllBytes());
                        log.error("[PAYMENT_APPROVE] 승인 거절 또는 에러 - Status: {}, Body: {}", res.getStatusCode(),
                                errorBody);
                        throw new IllegalStateException("카카오페이 승인 실패: " + errorBody);
                    })
                    .body(KakaoPayApproveResponse.class);

            if (response == null || response.tid() == null) {
                throw new IllegalStateException("비정상적인 승인 응답 수신");
            }

            log.info("[PAYMENT_APPROVE] 결제 최종 승인 완료 - AID: {}, TID: {}", response.aid(), response.tid());

        } catch (Exception e) {
            log.error("[PAYMENT_APPROVE] 네트워크 통신 장애 - Message: {}", e.getMessage());
            throw new RuntimeException("카카오페이 승인 API 통신 실패", e);
        }
    }

    // --- 내부 DTO (Record) 영역 ---

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