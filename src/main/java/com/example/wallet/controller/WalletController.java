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
 * 내부 서비스 간 통신(S2S) 및 게이트웨이를 통한 사용자 요청을 처리.
 */
@Slf4j
@RequestMapping("/wallet")
@RestController
@RequiredArgsConstructor
public class WalletController {

    /**
     * [상수 정의]
     * 헤더 키 값을 컴파일 타임 상수로 관리하여 오타 방지 및 유지보수성 향상
     */
    private static final String USER_ID_HEADER = "x-user-id";
    private static final String USER_ROLE_HEADER = "x-role";
    private static final String USER_NAME_HEADER = "x-user-name";

    private final WalletService walletService;

    /**
     * [지갑 서비스 연결 테스트]
     * 게이트웨이에서 전달된 헤더 정보를 확인하여 서비스 연결 상태를 점검함.
     * 
     * @param memberId   헤더에서 추출한 유저 ID
     * @param memberRole 헤더에서 추출한 권한 정보
     * @param memberName 헤더에서 추출한 유저 이름
     * @return 연결 확인 메시지
     */
    @GetMapping("/")
    public String getWallet(
            @RequestHeader(USER_ID_HEADER) Long memberId,
            @RequestHeader(USER_ROLE_HEADER) String memberRole,
            @RequestHeader(USER_NAME_HEADER) String memberName) {

        log.info(">>> [WALLET_TEST] 지갑 연결 확인 요청 - UserId: {}, Role: {}, Name: {}", memberId, memberRole, memberName);

        return "WalletController/wallet/ : " + memberId + " " + memberRole + " " + memberName + "님의 /wallet/ 요청받음";
    }

    /**
     * [실시간 잔액 조회]
     * 로그인 시 Core 서비스에서 호출하여 Redis에 등록할 최신 잔액 데이터를 제공함.
     * 헤더가 포함되지 않는 관리자 페이지에서의 호출도 고려하여 memberId를 RequestParam으로 받음.
     * 화이트 리스트에 등록된 IP만 접근가능하도록 auth.lua에서 접근 원천 차단.
     * @param memberId 잔액을 조회할 회원 식별자
     * @return 해당 회원의 실시간 지갑 잔액 (BigDecimal)
     */
    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance(@RequestParam("member_id") Long memberId) {
        log.info(">>> [BALANCE_FETCH] 실시간 잔액 조회 요청 시작 - MemberId: {}", memberId);

        // memberId가 존재하는지 검증
        if (memberId == null) {
            log.warn(">>> [BALANCE_FETCH] memberId 누락 - MemberId: {}", memberId);
            return ResponseEntity.badRequest().build();
        }

        BigDecimal balance = walletService.getBalance(memberId);

        log.info(">>> [BALANCE_FETCH] 잔액 조회 성공 - MemberId: {}, Balance: {}",
                memberId, balance);
        return ResponseEntity.ok(balance);
    }

}