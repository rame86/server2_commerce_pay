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

import com.example.config.KakaoPayProperties;
import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;
import com.example.payment.dto.response.PaymentHistoryResponseDTO;
import com.example.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/payment")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final KakaoPayProperties kakaoPayProperties;

    // 결제 내역 및 지갑 정보 조회
    @GetMapping("/")
    public ResponseEntity<PaymentHistoryResponseDTO> getMyPayment(
            @RequestHeader("X-User-Id") Long memberId) {

        PaymentHistoryResponseDTO response = paymentService.getPaymentHistory(memberId);
        return ResponseEntity.ok(response);
    }

    // 지갑 충전 요청
    /*
     * POST http://localhost/msa/pay/payment/charge
     * Content-Type: application/json
     * Authorization: Bearer ~~~~~JWT~~~~~
     * 
     * {
     * "payType": "kakao_pay",
     * "amount": 30000
     * }
     */
    @PostMapping("/charge")
    public ResponseEntity<ChargeReadyResponseDTO> chargePoint(
            @RequestHeader("X-User-Id") Long memberId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ChargeRequestDTO request) {

        // Authorization 헤더에서 Bearer를 제외한 순수 토큰만 추출
        String token = "";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 서비스로 토큰도 같이 넘김
        ChargeReadyResponseDTO response = paymentService.readyPayment(memberId, request, token);
        return ResponseEntity.ok(response);
    }

    // 결제 성공
    @GetMapping("/charge/kakaopay/success")
    public ResponseEntity<Void> approvePayment(
            @RequestParam("pg_token") String pgToken,
            @RequestParam("chargeId") UUID chargeId,
            @RequestHeader("X-User-Id") String memberId) {

        paymentService.approvePayment(chargeId, pgToken, memberId);

        // 프론트엔드의 결제 성공 처리 전용 HTML 페이지로 리다이렉트
        String baseUrl = kakaoPayProperties.frontendWalletUrl().replace("/user/wallet", "");
        String successPageUrl = baseUrl + "/payment-success.html";
        
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(successPageUrl))
                .build();
    }

    // 결제 실패 시 리다이렉트
    @GetMapping("/charge/kakaopay/fail")
    public ResponseEntity<Void> failPayment() {
        String baseUrl = kakaoPayProperties.frontendWalletUrl().replace("/user/wallet", "");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(baseUrl + "/payment-fail.html"))
                .build();
    }

    // 결제 취소 시 리다이렉트
    @GetMapping("/charge/kakaopay/cancel")
    public ResponseEntity<Void> cancelPayment() {
        String baseUrl = kakaoPayProperties.frontendWalletUrl().replace("/user/wallet", "");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(baseUrl + "/payment-cancel.html"))
                .build();
    }
}
