//src/main/java/com/example/payment/controller/UserDashboardController.java
package com.example.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment.dto.response.UserDetailPaymentResponseDTO;
import com.example.payment.service.UserDashboardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/payment/admin")
@RequiredArgsConstructor
public class UserDashboardController {

    private final UserDashboardService userDashboardService;

    /**
     * [유저 대시보드용] 특정 유저의 포인트 잔액 + 구매/포인트 내역 조회
     * GET /payment/admin/user-detail/{memberId}
     */
    @GetMapping("/user-detail/{memberId}")
    public ResponseEntity<UserDetailPaymentResponseDTO> getUserDetail(
            @PathVariable(name = "memberId") Long memberId) {
            
        log.info("[UserDashboard] 유저 ID {} 대시보드 데이터 REST 조회 요청", memberId);
        UserDetailPaymentResponseDTO response = userDashboardService.getUserDashboardDetail(memberId);
        return ResponseEntity.ok(response);
    }
}