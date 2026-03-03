package com.example.payment.controller;


import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment.dto.response.WalletResponseDTO;
import com.example.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/wallet")
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final PaymentService paymentService;

    @GetMapping("/")
    public String getWallet(
            @RequestHeader("x-user-id") Long userId,
            @RequestHeader("x-role") String role) {

        String response = ("WalletController/wallet/ : " + userId + " " + role + "의 /wallet/ 요청받음");
        return response;
    }

    @PostMapping("charge")
    public String pointCharge(@RequestHeader Map<String, String> headers) {
        headers.forEach((key, val) -> {
            log.info("키: " + key + ", 값: " + val);
        });

        String response = ("WalletController/wallet/ : " + headers.get("x-user-id") + " " + headers.get("x-role")
                + "의 /wallet/charge 요청받음");

        return response;
    }

    @GetMapping("/getall") // 3. HTTP GET 요청을 이 메서드와 연결
    public ResponseEntity<List<WalletResponseDTO>> getAllWallets() {
        
        // 서비스 계층에서 DB의 모든 지갑 정보를 가져옴
        List<WalletResponseDTO> wallets = paymentService.getAllWallets();
        
        // 4. ResponseEntity를 사용하여 200 OK 상태 코드와 함께 데이터를 반환
        return ResponseEntity.ok(wallets);
    }

    // 로그인 시 Core 서비스에서 호출하여 Redis에 등록할 잔액 조회
    @GetMapping("/balance")
    public ResponseEntity<WalletResponseDTO> getBalance(@RequestParam("member_id") Long memberId) {
        // [Self-Review] memberId 유효성 검증 로직 추가 가능
        WalletResponseDTO response = paymentService.getWalletPoints(memberId);
        return ResponseEntity.ok(response);
    }

}
