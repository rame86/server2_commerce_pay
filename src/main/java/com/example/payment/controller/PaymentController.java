// src/main/java/com/example/payment/controller/PaymentController.java
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

import com.example.admin.dto.admin.response.UserDetailPaymentResponseDTO;
import com.example.admin.service.UserDashboardService;
import com.example.config.FrontendUrlProperties;
import com.example.payment.dto.user.ChargeReadyResponseDTO;
import com.example.payment.dto.user.ChargeRequestDTO;
import com.example.payment.dto.user.PaymentHistoryResponseDTO;
import com.example.payment.service.charge.ChargeService;
import com.example.wallet.service.WalletService;

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

    /**
     * [상수 정의]
     * 헤더 키 값을 컴파일 타임 상수로 관리하여 오타 방지 및 유지보수성 향상
     */
    private static final String USER_ID_HEADER = "x-user-id";
    private static final String PARAM_PG_TOKEN = "pg_token";
    private static final String PARAM_CHARGE_ID = "chargeId";

    private final ChargeService chargeService;
    private final WalletService walletService;
    private final FrontendUrlProperties frontendUrl;
    private final UserDashboardService userDashboardService;

    /**
     * [결제 내역 조회]
     * 사용자의 현재 지갑 잔액과 전체 거래 히스토리를 반환함.
     */
    @GetMapping("/")
    public ResponseEntity<PaymentHistoryResponseDTO> getMyPayment(
            @RequestHeader(USER_ID_HEADER) Long memberId) {

        log.info(">>> [MY_PAYMENT] 조회 요청 시작 - MemberId: {}", memberId);
        
        // 지갑이 존재하지 않는 신규 회원의 경우, 지갑을 자동으로 생성 후 결과를 반환함.
        PaymentHistoryResponseDTO response = walletService.getPaymentHistory(memberId);
        return ResponseEntity.ok(response); // 200 OK 응답과 함께 결제 내역 반환
        /*
         * ResponseEntity 란?
         * HTTP 응답을 나타내는 클래스. 상태 코드, 헤더, 바디를 포함하여 클라이언트에게 반환할 수 있음.
         */

    }

    /**
     * [결제 준비 요청]
     * 사용자가 충전 금액을 입력하고 결제 버튼을 눌렀을 때 호출되며, 다음 순서로 처리됨:
     * 1. 내부 결제 원장 대기(PENDING) 상태로 생성
     * 2. 외부 PG사와 통신하여 결제 고유 번호(TID) 발급
     * 3. 클라이언트에게 결제창 URL 반환
     * PG사로부터 응답받은 결제 페이지 URL을 클라이언트에 전달하여, 사용자가 결제 수단을 선택하고 인증할 수 있도록 유도하는 사전 준비 단계
     * @param memberId   헤더에서 추출한 유저 식별자 (x-user-id)
     * @param authHeader 헤더에서 추출한 인증 토큰 (Authorization)
     * @param request    요청 바디 (결제 수단, 충전 요청 금액)
     * @return ChargeReadyResponseDTO (내부 결제 ChargeId, PG사 TID, 프론트 리다이렉트 URL 포함)
     */
    @PostMapping("/charge")
    public ResponseEntity<ChargeReadyResponseDTO> chargePoint(
            @RequestHeader(USER_ID_HEADER) Long memberId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ChargeRequestDTO request) {

        log.info(">>> [CHARGE_READY] 충전 준비 요청 시작 - MemberId: {}, PayType: {}, Amount: {}",
                memberId, request.getPayType(), request.getAmount());

        // 내부 결제 원장 생성 및 PG사 TID 발급 (Service 계층 위임)
        ChargeReadyResponseDTO response = chargeService.readyPayment(memberId, request);

        log.info(">>> [CHARGE_READY] 준비 처리 완료 - ChargeId: {}, TID: {}", response.chargeId(), response.providerTid());

        return ResponseEntity.ok(response);
    }

    /**
     * [결제 승인 콜백 - 카카오페이]
     * 사용자가 카카오페이 결제창에서 인증을 마치면 카카오 측에서 호출하는 엔드포인트로. 다음 순서로 처리됨:
     * 1. PG사 인증 토큰(pg_token) 및 내부 식별자(chargeId) 수신
     * 2. 외부 PG사 최종 승인 API 호출 및 포인트 적립 (Service 계층 위임)
     * 3. 결제 완료 후 프론트엔드 결과 페이지로 리다이렉트
     * 리다이랙트 시에도 게이트웨이의 auth.lua에서 사용자 인증이 수행되므로, chargeId와 memberId를 쿼리 파라미터 및 헤더로 전달하여 무결성을 확보함.
      * @param pgToken  카카오페이 결제 승인 요청을 위한 인증 토큰 (pg_token)
      * @param chargeId 내부 결제 원장 식별자 (결제 준비 요청 시 생성한 UUID)
      * @param memberId 헤더에서 추출한 유저 식별자 (x-user-id)
      * @return 성공 페이지로의 리다이렉트 응답
     */
    @GetMapping("/charge/kakaopay/success")
    public ResponseEntity<Void> approvePayment(
            @RequestParam(PARAM_PG_TOKEN) String pgToken,
            @RequestParam(PARAM_CHARGE_ID) UUID chargeId,
            @RequestHeader(USER_ID_HEADER) String memberId) {

        log.info(">>> [CHARGE_APPROVE] 승인 콜백 수신 - ChargeId: {}, MemberId: {}", chargeId, memberId);

        // PG사 최종 승인 처리 (Service 계층 위임)
        chargeService.approvePayment(chargeId, pgToken, memberId);

        log.info(">>> [CHARGE_APPROVE] 최종 승인 성공 - 프론트엔드 리다이렉트 수행");

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
        log.warn(">>> [CHARGE_FAIL] 결제 실패 콜백 수신 - 실패 페이지 리다이렉트");
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
        log.info(">>> [CHARGE_CANCEL] 결제 취소 콜백 수신 - 취소 페이지 리다이렉트");
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

        log.info(">>> [USER_DETAIL] 대시보드 데이터 조회 요청 시작 - TargetMemberId: {}", memberId);
        UserDetailPaymentResponseDTO response = userDashboardService.getUserDashboardDetail(memberId);
        return ResponseEntity.ok(response);
    }
}