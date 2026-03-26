// src/main/java/com/example/payment/service/charge/provider/KakaoPayProvider.java
package com.example.payment.service.charge.provider;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.config.KakaoPayProperties;
import com.example.payment.domain.Charge;
import com.example.payment.dto.response.ChargeReadyResponseDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * [카카오페이 결제 연동 구현체]
 * PaymentProvider 인터페이스의 카카오페이 전용 구현체로, 외부 API 통신을 담당함.
 * RestClient를 사용하여 선언적이고 효율적인 HTTP 요청을 수행함.
 */
@Slf4j
@Component
public class KakaoPayProvider implements PaymentProvider {

    private final KakaoPayProperties properties;
    private final RestClient restClient;

    /**
     * [생성자 및 RestClient 초기화]
     * 카카오페이 v1 API 규격에 따라 Secret Key 인증 헤더와 공통 설정을 주입함.
     */
    public KakaoPayProvider(KakaoPayProperties properties) {
        log.info(">>> [KAKAO_PAY_INIT] 카카오페이 프로바이더 초기화 시작");
        this.properties = properties;

        this.restClient = RestClient.builder()
                .baseUrl(properties.kakaoPayBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 카카오페이 보안 인증: 'SECRET_KEY ' 접두사와 함께 발급받은 키를 헤더에 포함
                .defaultHeader(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + properties.secretKey())
                .build();
    }

    /**
     * [지원 여부 확인]
     * 전달된 PG사 식별자가 "KAKAO_PAY"인 경우 해당 빈이 결제 로직을 수행함.
     */
    @Override
    public boolean supports(String pgProvider) {
        boolean isSupported = "KAKAO_PAY".equals(pgProvider);
        if (isSupported) {
            log.debug(">>> [STRATEGY_CHECK] 카카오페이 프로바이더 선택됨");
        }
        return isSupported;
    }

    /**
     * [STEP 1: 결제 준비 (Ready API)]
     * 카카오페이 서버에 결제 정보를 등록하고, 결제창 진입을 위한 TID와 리다이렉트 URL을 발급받음.
     * @param charge   결제 요청 원장 엔티티
     * @param memberId 사용자 식별자
     * @param token    사용자 세션 또는 인증 토큰
     * @return 발급된 TID와 결제창 URL을 포함한 응답 DTO
     */
    @Override
    public ChargeReadyResponseDTO ready(Charge charge, Long memberId, String token) {
        log.info(">>> [PAYMENT_READY] 카카오페이 준비 단계 진입 - ChargeId: {}, Amount: {}", 
                charge.getChargeId(), charge.getAmount());

        // 승인 성공 시 콜백 URL에 내부 식별자(chargeId)를 쿼리 파라미터로 바인딩하여 무결성 확보
        String approvalUrlWithToken = properties.approvalUrl() + "?chargeId=" + charge.getChargeId();

        // 카카오페이 API 규격에 맞춘 결제 준비 요청 객체 조립
        KakaoPayReadyRequest request = new KakaoPayReadyRequest(
                properties.cid(),
                charge.getChargeId().toString(), // 가맹점 주문번호 (내부 UUID 사용)
                charge.getWalletId().toString(), // 가맹점 회원 ID
                "포인트 충전",
                1,
                charge.getAmount().intValue(),
                0, 0,
                approvalUrlWithToken,
                properties.cancelUrl(),
                properties.failUrl());

        try {
            log.info(">>> [EXTERNAL_API_REQ] 카카오페이 Ready API 호출 시작 - URL: /online/v1/payment/ready");
            
            KakaoPayReadyResponse response = restClient.post()
                    .uri("/online/v1/payment/ready")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        log.error(">>> [PAYMENT_READY_ERROR] API 응답 오류 - StatusCode: {}", res.getStatusCode());
                        throw new IllegalStateException("카카오페이 준비 API 호출 실패");
                    })
                    .body(KakaoPayReadyResponse.class);

            if (response == null || response.tid() == null) {
                log.error(">>> [PAYMENT_READY_ERROR] 응답 데이터가 비어있거나 TID가 누락됨");
                throw new IllegalStateException("카카오페이 결제 준비 응답 오류: TID 누락");
            }

            log.info(">>> [PAYMENT_READY] 결제 준비 성공 - TID: {}, RedirectURL: {}", 
                    response.tid(), response.next_redirect_pc_url());

            return ChargeReadyResponseDTO.builder()
                    .chargeId(charge.getChargeId())
                    .payType("KAKAOPAY")
                    .nextRedirectUrl(response.next_redirect_pc_url())
                    .providerTid(response.tid())
                    .build();

        } catch (RestClientException e) {
            log.error(">>> [PAYMENT_READY_EXCEPTION] 네트워크 통신 장애 발생 - Message: {}", e.getMessage());
            throw new RuntimeException("카카오페이 API 네트워크 통신 실패", e);
        }
    }

    /**
     * [STEP 2: 결제 승인 (Approve API)]
     * 사용자의 인증 결과물인 pg_token을 카카오페이 측에 전달하여 실제 결제를 확정함.
     * @param charge  TID가 포함된 결제 원장
     * @param pgToken 사용자 인증 후 발급된 성공 토큰
     */
    @Override
    public void approve(Charge charge, String pgToken) {
        log.info(">>> [PAYMENT_APPROVE] 카카오페이 승인 단계 진입 - ChargeId: {}, TID: {}", 
                charge.getChargeId(), charge.getPgTransactionId());

        // 준비 단계에서 저장한 TID와 수신한 pg_token을 결합하여 승인 요청
        KakaoPayApproveRequest request = new KakaoPayApproveRequest(
                properties.cid(),
                charge.getPgTransactionId(),
                charge.getChargeId().toString(),
                charge.getWalletId().toString(),
                pgToken);

        try {
            log.info(">>> [EXTERNAL_API_REQ] 카카오페이 Approve API 호출 시작 - URL: /online/v1/payment/approve");

            KakaoPayApproveResponse response = restClient.post()
                    .uri("/online/v1/payment/approve")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        String errorBody = new String(res.getBody().readAllBytes());
                        log.error(">>> [PAYMENT_APPROVE_ERROR] 승인 거절 - Status: {}, Body: {}", 
                                res.getStatusCode(), errorBody);
                        throw new IllegalStateException("카카오페이 승인 실패: " + errorBody);
                    })
                    .body(KakaoPayApproveResponse.class);

            if (response == null || response.tid() == null) {
                log.error(">>> [PAYMENT_APPROVE_ERROR] 비정상적인 승인 응답 수신");
                throw new IllegalStateException("비정상적인 승인 응답 수신");
            }

            log.info(">>> [PAYMENT_APPROVE] 결제 최종 승인 완료 - AID: {}, TID: {}", 
                    response.aid(), response.tid());

        } catch (Exception e) {
            log.error(">>> [PAYMENT_APPROVE_EXCEPTION] 로직 또는 통신 에러 - Message: {}", e.getMessage());
            throw new RuntimeException("카카오페이 승인 API 통신 실패", e);
        }
    }

    // --- 카카오페이 API 규격 전용 내부 Record ---

    /** [Ready Request] 카카오페이 준비 요청 명세 */
    private record KakaoPayReadyRequest(
            String cid, String partner_order_id, String partner_user_id,
            String item_name, Integer quantity, Integer total_amount,
            Integer vat_amount, Integer tax_free_amount,
            String approval_url, String cancel_url, String fail_url) {
    }

    /** [Ready Response] 카카오페이 준비 응답 명세 (TID 및 URL 포함) */
    private record KakaoPayReadyResponse(
            String tid, String next_redirect_app_url, String next_redirect_mobile_url,
            String next_redirect_pc_url, String android_app_scheme,
            String ios_app_scheme, String created_at) {
    }

    /** [Approve Request] 카카오페이 승인 요청 명세 */
    private record KakaoPayApproveRequest(
            String cid, String tid, String partner_order_id,
            String partner_user_id, String pg_token) {
    }

    /** [Approve Response] 카카오페이 승인 성공 응답 명세 */
    private record KakaoPayApproveResponse(
            String aid, String tid, String cid,
            String partner_order_id, String partner_user_id,
            String payment_method_type, String approved_at) {
    }
}