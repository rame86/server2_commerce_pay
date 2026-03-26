// src/main/java/com/example/payment/service/provider/KakaoPayProvider.java

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
 * [카카오페이 결제 구현체]
 * PaymentProvider 인터페이스를 상속받아 카카오페이 외부 API와의 통신을 전담.
 * Spring의 최신 RestClient를 사용하여 동기 방식으로 API를 호출.
 */
@Slf4j
@Component
public class KakaoPayProvider implements PaymentProvider {

    private final KakaoPayProperties properties;
    private final RestClient restClient;

    public KakaoPayProvider(KakaoPayProperties properties) {
        this.properties = properties;

        // RestClient 초기화: 공통 헤더(Content-Type, Secret Key 인증)를 미리 설정
        this.restClient = RestClient.builder()
                .baseUrl(properties.kakaoPayBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 카카오페이 API v1: Admin Key 대신 Secret Key를 헤더에 포함하여 보안성 강화
                .defaultHeader(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + properties.secretKey())
                .build();
    }

    /**
     * 전략 패턴 식별: 전달된 PG사 코드가 "KAKAO_PAY"일 경우 이 빈(Bean)이 선택됨
     */
    @Override
    public boolean supports(String pgProvider) {
        return "KAKAO_PAY".equals(pgProvider);
    }

    /**
     * [STEP 1: 결제 준비 (Ready API)]
     * 카카오페이 측에 결제 정보를 전달하고, 사용자에게 보여줄 결제 페이지 URL과
     * 해당 거래를 식별할 TID(Transaction ID)를 발급.
     */
    @Override
    public ChargeReadyResponseDTO ready(Charge charge, Long memberId, String token) {
        log.info("[PAYMENT_READY] 카카오페이 결제 준비 요청 - ChargeID: {}", charge.getChargeId());

        // 결제 승인 성공 시 리다이렉트될 URL에 내부 관리용 chargeId를 쿼리 파라미터로 추가
        // 이를 통해 나중에 프론트엔드가 승인 요청을 보낼 때 어떤 결제건인지 식별 가능
        String approvalUrlWithToken = properties.approvalUrl() + "?chargeId=" + charge.getChargeId();

        // 카카오페이 API 규격에 맞춘 요청 객체 생성
        KakaoPayReadyRequest request = new KakaoPayReadyRequest(
                properties.cid(), // 가맹점 코드 (테스트용 TC0ONETIME 등)
                charge.getChargeId().toString(), // 가맹점 주문번호 (내부 원장 ID)
                charge.getWalletId().toString(), // 가맹점 회원 ID
                "포인트 충전", // 상품명
                1, // 상품 수량
                charge.getAmount().intValue(), // 총 결제 금액
                0, // 부가세 (0원 설정 시 자동계산 혹은 비과세)
                0, // 비과세 금액
                approvalUrlWithToken, // 결제 성공 시 리다이렉트 URL
                properties.cancelUrl(), // 결제 취소 시 리다이렉트 URL
                properties.failUrl()); // 결제 실패 시 리다이렉트 URL

        try {
            // 카카오페이 준비 API 호출 (/online/v1/payment/ready)
            KakaoPayReadyResponse response = restClient.post()
                    .uri("/online/v1/payment/ready")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        // 4xx, 5xx 에러 발생 시 로그를 남기고 예외 발생
                        log.error("[PAYMENT_READY] API 에러 발생 - Status: {}", res.getStatusCode());
                        throw new IllegalStateException("카카오페이 준비 API 호출 실패");
                    })
                    .body(KakaoPayReadyResponse.class);

            if (response == null || response.tid() == null) {
                throw new IllegalStateException("카카오페이 결제 준비 응답 오류: TID 누락");
            }

            log.info("[PAYMENT_READY] 결제 준비 완료 - TID: {}", response.tid());

            // 내부 DTO로 변환하여 반환 (TID와 PC용 리다이렉트 URL 포함)
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
     * [STEP 2: 결제 승인 (Approve API)]
     * 사용자가 카카오페이 화면에서 인증을 마치면 발급되는 pg_token을 사용하여
     * 카카오페이 측에 실제로 돈을 결제하라고 최종 확정 요청.
     */
    @Override
    public void approve(Charge charge, String pgToken) {
        log.info("[PAYMENT_APPROVE] 카카오페이 결제 승인 요청 - ChargeID: {}, TID: {}",
                charge.getChargeId(), charge.getPgTransactionId());

        // 승인 요청 객체 생성 (준비 단계에서 받은 TID와 사용자의 pg_token이 필수)
        KakaoPayApproveRequest request = new KakaoPayApproveRequest(
                properties.cid(),
                charge.getPgTransactionId(), // 결제 준비 단계에서 발급받았던 TID
                charge.getChargeId().toString(),
                charge.getWalletId().toString(),
                pgToken); // 사용자 인증 결과 토큰

        try {
            // 카카오페이 승인 API 호출 (/online/v1/payment/approve)
            KakaoPayApproveResponse response = restClient.post()
                    .uri("/online/v1/payment/approve")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        // 카카오페이가 보낸 상세 에러 바디를 읽어 로그 기록 (디버깅 용도)
                        String errorBody = new String(res.getBody().readAllBytes());
                        log.error("[PAYMENT_APPROVE] 승인 거절 또는 에러 - Status: {}, Body: {}",
                                res.getStatusCode(), errorBody);
                        throw new IllegalStateException("카카오페이 승인 실패: " + errorBody);
                    })
                    .body(KakaoPayApproveResponse.class);

            if (response == null || response.tid() == null) {
                throw new IllegalStateException("비정상적인 승인 응답 수신");
            }

            // 승인 성공 시 로그를 남기며 AID(승인 ID) 기록
            log.info("[PAYMENT_APPROVE] 결제 최종 승인 완료 - AID: {}, TID: {}", response.aid(), response.tid());

        } catch (Exception e) {
            log.error("[PAYMENT_APPROVE] API 통신 또는 로직 에러 - Message: {}", e.getMessage());
            throw new RuntimeException("카카오페이 승인 API 통신 실패", e);
        }
    }

    // --- 내부 DTO (Java 16+ Record 사용) ---
    // Record를 사용하여 불변(Immutable) 데이터 객체를 간결하게 정의

    /* 카카오페이 준비 요청 데이터 */
    private record KakaoPayReadyRequest(
            String cid, String partner_order_id, String partner_user_id,
            String item_name, Integer quantity, Integer total_amount,
            Integer vat_amount, Integer tax_free_amount,
            String approval_url, String cancel_url, String fail_url) {
    }

    /* 카카오페이 준비 응답 데이터 */
    private record KakaoPayReadyResponse(
            String tid, String next_redirect_app_url, String next_redirect_mobile_url,
            String next_redirect_pc_url, String android_app_scheme,
            String ios_app_scheme, String created_at) {
    }

    /** 카카오페이 승인 요청 데이터 */
    private record KakaoPayApproveRequest(
            String cid, String tid, String partner_order_id,
            String partner_user_id, String pg_token) {
    }

    /* 카카오페이 승인 응답 데이터 (결제 수단, 일시 등 포함) */
    private record KakaoPayApproveResponse(
            String aid, String tid, String cid,
            String partner_order_id, String partner_user_id,
            String payment_method_type, String approved_at) {
    }
}