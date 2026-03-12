package com.example.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment.dto.response.PaymentHistoryResponseDTO;
import com.example.payment.dto.response.SettlementDTO;

import lombok.RequiredArgsConstructor;


@RequestMapping("/admin")
@RestController
// @RequiredArgsConstructor
public class PaymentAdminController {

    // 헤더 상수화
    private static final String USER_ID_HEADER = "x-user-id";
    private static final String USER_NAME_HEADER = "x-user-name";
    private static final String USER_ROLE_HEADER = "x-role";
    

    // // 거래액 요약
    // @GetMapping("/")
    // public ResponseEntity<SettlementDTO> getSettlement(
    //         @RequestHeader(USER_ID_HEADER) String member_id,
    //         @RequestHeader(USER_NAME_HEADER) String name,
    //     @RequestHeader(USER_ROLE_HEADER) String roll) {

    //     SettlementDTO response;
    //     return ResponseEntity.ok(response);
    // }

}
