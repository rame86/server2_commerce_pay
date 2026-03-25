// src/main/java/com/example/settlement/controller/ArtistSettlementController.java
package com.example.settlement.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.settlement.dto.ArtistSettlementResponseDTO;
import com.example.settlement.service.ArtistSettlementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/artist")
@RequiredArgsConstructor
public class ArtistSettlementController {

    private final ArtistSettlementService artistSettlementService;

    /**
     * [아티스트 정산 대시보드 조회]
     * GET /artist/settlement
     * - 이번달 수익, 누적 수익, 정산 예정/완료 요약
     * - 최근 6개월 월별 수익 트렌드
     * - 수익 구성 비율 (이벤트/굿즈/후원)
     * - 정산 내역 목록
     */
    @GetMapping("/settlement")
    public ResponseEntity<ArtistSettlementResponseDTO> getSettlement(
            @RequestHeader("x-user-id") Long artistId) {

        log.info("[ARTIST SETTLEMENT] 정산 대시보드 요청 - artistId: {}", artistId);
        ArtistSettlementResponseDTO response = artistSettlementService.getSettlementDashboard(artistId);
        return ResponseEntity.ok(response);
    }
}
