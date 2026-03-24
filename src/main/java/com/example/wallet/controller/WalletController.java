package com.example.wallet.controller;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment.domain.ArtistAccount;
import com.example.settlement.service.SettlementService;
import com.example.wallet.dto.ArtistAccountResponse;
import com.example.wallet.service.WalletService;

import org.springframework.web.bind.annotation.PathVariable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/wallet")
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final SettlementService settlementService;

    @GetMapping("/")
    public String getWallet(
            @RequestHeader("x-user-id") Long userId,
            @RequestHeader("x-role") String role,
            @RequestHeader("x-user-name") String name) {

        String response = ("WalletController/wallet/ : " + userId + " " + role + " " + name + "님의 /wallet/ 요청받음");
        return response;
    }

    // 로그인 시 Core 서비스에서 호출하여 Redis에 등록할 잔액 조회
    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance(@RequestParam("member_id") Long memberId) {
        // [Self-Review] memberId 유효성 검증 로직 추가 가능
        BigDecimal balance = walletService.getBalance(memberId);
        return ResponseEntity.ok(balance);
    }

    // 관리자 서비스에서 아티스트 상세 조회 시 호출하는 엔드포인트 복구
    @GetMapping("/artist/{artistId}")
    public ResponseEntity<ArtistAccountResponse> getArtistAccount(@PathVariable("artistId") Long artistId) {
        log.info("[WALLET] 아티스트 계좌 정보 조회 요청 - artistId: {}", artistId);
        ArtistAccount account = settlementService.getArtistAccount(artistId);

        ArtistAccountResponse response = ArtistAccountResponse.builder()
                .totalBalance(account.getTotalBalance())
                .withdrawableBalance(account.getWithdrawableBalance())
                .build();

        return ResponseEntity.ok(response);
    }
}
