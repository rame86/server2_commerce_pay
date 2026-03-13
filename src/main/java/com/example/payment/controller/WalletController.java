package com.example.payment.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment.dto.response.WalletResponseDTO;
import com.example.payment.service.wallet.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/wallet")
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/")
    public String getWallet(
            @RequestHeader("x-user-id") Long userId,
            @RequestHeader("x-role") String role,
            @RequestHeader("x-user-name") String name) {

        String response = ("WalletController/wallet/ : " + userId + " " + role +" "+name+ "님의 /wallet/ 요청받음");
        return response;
    }

    // 로그인 시 Core 서비스에서 호출하여 Redis에 등록할 잔액 조회
    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance(@RequestParam("member_id") Long memberId) {
        // [Self-Review] memberId 유효성 검증 로직 추가 가능
        BigDecimal balance = walletService.getBalance(memberId);
        return ResponseEntity.ok(balance);
    }

    // 관리자용
    @GetMapping("/getall") // 3. HTTP GET 요청을 이 메서드와 연결
    public ResponseEntity<List<WalletResponseDTO>> getAllWallets() {

        // 서비스 계층에서 DB의 모든 지갑 정보를 가져옴
        List<WalletResponseDTO> wallets = walletService.getAllWallets();

        // 4. ResponseEntity를 사용하여 200 OK 상태 코드와 함께 데이터를 반환
        return ResponseEntity.ok(wallets);
    }

}
