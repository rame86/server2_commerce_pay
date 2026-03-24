// src/main/java/com/example/settlement/service/ArtistSettlementService.java
package com.example.settlement.service;

import com.example.settlement.dto.ArtistSettlementResponseDTO;

public interface ArtistSettlementService {
    /**
     * 아티스트 정산 내역 전체 조회
     * (요약 카드, 월별 트렌드, 수익 구성, 내역 목록)
     */
    ArtistSettlementResponseDTO getSettlementDashboard(Long artistId);
}
