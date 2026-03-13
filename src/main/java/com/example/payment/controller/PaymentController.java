//src/main/java/com/example/payment/controller/PaymentController.java
package com.example.payment.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.config.FrontendUrlProperties;
import com.example.config.KakaoPayProperties;
import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;
import com.example.payment.dto.response.PaymentHistoryResponseDTO;
import com.example.payment.service.charge.ChargeService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/payment")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    // 헤더 및 파라미터 상수화
    private static final String USER_ID_HEADER = "x-user-id";
    private static final String PARAM_PG_TOKEN = "pg_token";
    private static final String PARAM_CHARGE_ID = "chargeId";

    private final ChargeService chargeService;
    private final KakaoPayProperties kakaoPayProperties;
    private final FrontendUrlProperties frontendUrl;

    /**
     * [결제 내역 및 지갑 정보 조회]
     */
    @GetMapping("/")
    public ResponseEntity<PaymentHistoryResponseDTO> getMyPayment(
            @RequestHeader(USER_ID_HEADER) Long memberId) {

        PaymentHistoryResponseDTO response = chargeService.getPaymentHistory(memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * [지갑 포인트 충전 준비 요청]
     */
    @PostMapping("/charge")
    public ResponseEntity<ChargeReadyResponseDTO> chargePoint(
            @RequestHeader(USER_ID_HEADER) Long memberId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ChargeRequestDTO request) {

        String token = "";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        ChargeReadyResponseDTO response = chargeService.readyPayment(memberId, request, token);
        return ResponseEntity.ok(response);
    }

    /**
     * [결제 승인 처리 (PG사 콜백)]
     */
    @GetMapping("/charge/kakaopay/success")
    public ResponseEntity<Void> approvePayment(
            @RequestParam(PARAM_PG_TOKEN) String pgToken,
            @RequestParam(PARAM_CHARGE_ID) UUID chargeId,
            @RequestHeader(USER_ID_HEADER) String memberId) {

        chargeService.approvePayment(chargeId, pgToken, memberId);

        // yml에서 주입받은 URL을 바로 사용
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl.success()))
                .build();
    }

    /**
     * [결제 실패 리다이렉트]
     */
    @GetMapping("/charge/kakaopay/fail")
    public ResponseEntity<Void> failPayment() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl.fail()))
                .build();
    }

    /**
     * [결제 취소 리다이렉트]
     */
    @GetMapping("/charge/kakaopay/cancel")
    public ResponseEntity<Void> cancelPayment() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl.cancel()))
                .build();
    }
}