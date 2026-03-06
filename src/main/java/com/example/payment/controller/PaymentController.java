//src/main/java/com/example/payment/controller/PaymentController.java
package com.example.payment.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;
import com.example.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/payment")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/")
    public String hello() {
        return "서버가 정상적으로 실행 중!";
    }

    // 지갑 충전 요청
    /*      
     POST http://localhost/msa/pay/payment/charge
     Content-Type: application/json
     Authorization: Bearer ~~~~~JWT~~~~~
     
     {
      "payType": "kakao_pay",
      "chargeAmount": 30000
     }
     */
    @PostMapping("/charge")
    public ResponseEntity<ChargeReadyResponseDTO> chargePoint(
            @RequestHeader("X-User-Id") Long memberId,
            @RequestBody ChargeRequestDTO request) {

        // 서비스로 처리를 위임하고 공통 규격의 응답을 반환
        ChargeReadyResponseDTO response = paymentService.readyPayment(memberId, request);
        return ResponseEntity.ok(response);
    }

    // 카카오페이 결제 승인 콜백 (사용자 인증 완료 후 자동 리다이렉트 됨)
    @GetMapping("/success")
    public ResponseEntity<String> approvePayment(
            @RequestParam("pg_token") String pgToken,
            @RequestParam("chargeId") UUID chargeId,
            @RequestParam("memberId") String memberId) {

        paymentService.approvePayment(chargeId, pgToken, memberId);
        return ResponseEntity.ok("결제 및 충전이 완료되었습니다.");
    }

}
