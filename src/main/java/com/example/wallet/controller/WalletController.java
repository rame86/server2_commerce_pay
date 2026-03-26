// src/main/java/com/example/wallet/controller/WalletController.java
package com.example.wallet.controller;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [지갑 서비스 컨트롤러]
 * 사용자의 지갑 연결 테스트 및 실시간 잔액 조회를 담당함.
 * 내부 서비스 간 통신(Internal Call) 및 게이트웨이를 통한 사용자 요청을 처리.
 */
@Slf4j
@RequestMapping("/wallet")
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * [지갑 서비스 연결 테스트]
     * 게이트웨이에서 전달된 헤더 정보를 확인하여 서비스 연결 상태를 점검함.
     * @param userId 헤더에서 추출한 유저 ID
     * @param role   헤더에서 추출한 권한 정보
     * @param name   헤더에서 추출한 유저 이름
     * @return 연결 확인 메시지
     */
    @GetMapping("/")
    public String getWallet(
            @RequestHeader("x-user-id") Long userId,
            @RequestHeader("x-role") String role,
            @RequestHeader("x-user-name") String name) {

        log.info(">>> [WALLET_TEST] 지갑 연결 확인 요청 - UserId: {}, Role: {}, Name: {}", userId, role, name);

        return "WalletController/wallet/ : " + userId + " " + role + " " + name + "님의 /wallet/ 요청받음";
    }

    /**
     * [실시간 잔액 조회]
     * 로그인 시 Core 서비스에서 호출하여 Redis에 등록할 최신 잔액 데이터를 제공함.
     * @param memberId 잔액을 조회할 회원 식별자
     * @return 해당 회원의 실시간 지갑 잔액 (BigDecimal)
     */
    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance(@RequestParam("member_id") Long memberId) {
        log.info(">>> [BALANCE_FETCH] 실시간 잔액 조회 요청 시작 - MemberId: {}", memberId);

        // [Self-Review] memberId 유효성 검증 로직 추가 가능 (비어있거나 잘못된 형식인지 확인)
        BigDecimal balance = walletService.getBalance(memberId);

        log.info(">>> [BALANCE_FETCH] 잔액 조회 성공 - MemberId: {}, Balance: {}", memberId, balance);
        return ResponseEntity.ok(balance);
    }

}