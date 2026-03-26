//src/main/java/com/example/payment/controller/PaymentController.java
package com.example.payment.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.config.FrontendUrlProperties;
import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;
import com.example.payment.dto.response.PaymentHistoryResponseDTO;
import com.example.payment.dto.response.UserDetailPaymentResponseDTO;
import com.example.payment.service.UserDashboardService;
import com.example.payment.service.charge.ChargeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [결제 API 컨트롤러]
 * 외부 결제 요청 수신, PG사 콜백 처리 및 프론트엔드 리다이렉트를 관리함.
 */
@Slf4j
@RequestMapping("/payment")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private static final String USER_ID_HEADER = "x-user-id";
    private static final String PARAM_PG_TOKEN = "pg_token";
    private static final String PARAM_CHARGE_ID = "chargeId";

    private final ChargeService chargeService;    
    private final FrontendUrlProperties frontendUrl;
    private final UserDashboardService userDashboardService;

    /**
     * [결제 내역 조회]
     * 사용자의 현재 지갑 잔액과 전체 거래 히스토리를 반환함.
     */
    @GetMapping("/")
    public ResponseEntity<PaymentHistoryResponseDTO> getMyPayment(
            @RequestHeader(USER_ID_HEADER) Long memberId) {
        
        log.info("[MY_PAYMENT] 조회 요청 - memberId: {}", memberId);
        PaymentHistoryResponseDTO response = chargeService.getPaymentHistory(memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * [결제 준비 요청]
     * 사용자가 충전 금액을 입력하고 결제 버튼을 눌렀을 때 호출됨.
     * PG사로부터 TID를 발급받고 결제창 URL을 반환함.
     */
    @PostMapping("/charge")
    public ResponseEntity<ChargeReadyResponseDTO> chargePoint(
            @RequestHeader(USER_ID_HEADER) Long memberId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ChargeRequestDTO request) {

        log.info("[CHARGE_READY] 충전 준비 요청 - memberId: {}, payType: {}, amount: {}", 
                memberId, request.getPayType(), request.getAmount());

        String token = "";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        ChargeReadyResponseDTO response = chargeService.readyPayment(memberId, request, token);
        log.info("[CHARGE_READY] 준비 완료 - chargeId: {}, TID: {}", response.chargeId(), response.providerTid());
        
        return ResponseEntity.ok(response);
    }

    /**
     * [결제 승인 콜백]
     * 사용자가 PG사 결제창에서 인증을 마치면 PG사가 호출하는 엔드포인트.
     * 내부 승인 로직 완료 후 사용자를 서비스 완료 페이지로 리다이렉트함.
     */
    @GetMapping("/charge/kakaopay/success")
    public ResponseEntity<Void> approvePayment(
            @RequestParam(PARAM_PG_TOKEN) String pgToken,
            @RequestParam(PARAM_CHARGE_ID) UUID chargeId,
            @RequestHeader(USER_ID_HEADER) String memberId) {

        log.info("[CHARGE_APPROVE] 승인 콜백 수신 - chargeId: {}, memberId: {}", chargeId, memberId);

        chargeService.approvePayment(chargeId, pgToken, memberId);

        log.info("[CHARGE_APPROVE] 승인 처리 성공 - 프론트엔드 리다이렉트");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl.success()))
                .build();
    }

    /**
     * [결제 실패 콜백]
     * 결제 과정 중 한도 초과, 카드 오류 등으로 실패 시 프론트엔드 실패 페이지로 이동함.
     */
    @GetMapping("/charge/kakaopay/fail")
    public ResponseEntity<Void> failPayment() {
        log.warn("[CHARGE_FAIL] 결제 실패 콜백 수신 - 실패 페이지 리다이렉트");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl.fail()))
                .build();
    }

    /**
     * [결제 취소 콜백]
     * 사용자가 결제창을 닫거나 직접 취소 버튼을 눌렀을 때 호출됨.
     */
    @GetMapping("/charge/kakaopay/cancel")
    public ResponseEntity<Void> cancelPayment() {
        log.info("[CHARGE_CANCEL] 결제 취소 콜백 수신 - 취소 페이지 리다이렉트");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl.cancel()))
                .build();
    }

    /**
     * [사용자 상세 대시보드 데이터 조회]
     * 관리자 도구 등에서 특정 사용자의 결제 통계 및 상세 데이터를 조회함.
     */
    @GetMapping("/user-detail/{memberId}")
    public ResponseEntity<UserDetailPaymentResponseDTO> getUserDetail(
            @PathVariable(name = "memberId") Long memberId) {
            
        log.info("[USER_DETAIL] 대시보드 데이터 조회 요청 - targetMemberId: {}", memberId);
        UserDetailPaymentResponseDTO response = userDashboardService.getUserDashboardDetail(memberId);
        return ResponseEntity.ok(response);
    }
}