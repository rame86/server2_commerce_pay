// src/main/java/com/example/settlement/dto/ArtistSettlementResponseDTO.java
package com.example.settlement.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;

/**
 * GET /artist/settlement 응답 최상위 DTO
 * 화면의 요약 카드, 월별 트렌드, 수익 구성, 정산 내역 목록을 모두 담습니다.
 */
@Builder
public record ArtistPayoutResponseDTO(

        // 상단 요약 카드 4개
        BigDecimal thisMonthRevenue,        // 이번달 수익
        BigDecimal totalAccumulatedRevenue, // 누적 수익
        BigDecimal pendingSettlement,       // 정산 예정 (status=COMPLETED 중 아직 지급 안된 것)
        BigDecimal completedSettlement,     // 정산 완료 금액
        int        completedCount,          // 정산 완료 횟수

        // 월별 수익 트렌드 (그래프용)
        List<MonthlyRevenueSummary> monthlyTrend,

        // 수익 구성 (도넛 차트용)
        List<RevenueComposition> revenueComposition,

        // 정산 내역 목록 (하단 리스트)
        List<SettlementSummary> settlements

) {

    /** 월별 수익 요약 (ex: 2025-10월 이벤트 5,000,000 / 굿즈 2,000,000) */
    @Builder
    public record MonthlyRevenueSummary(
            String yearMonth,           // "yyyy-MM"
            BigDecimal eventRevenue,    // 이벤트 예매 수익
            BigDecimal goodsRevenue,    // 굿즈 판매 수익
            BigDecimal donationRevenue  // 팬 후원 수익
    ) {}

    /** 수익 구성 항목 (도넛 차트 1 슬라이스) */
    @Builder
    public record RevenueComposition(
            String type,        // ex: "이벤트 예매" / "굿즈 판매" / "팬 후원"
            BigDecimal amount,
            double percentage   // ex: 52.0
    ) {}

    /** 정산 내역 목록 1건 */
    @Builder
    public record SettlementSummary(
            String period,          // ex: "2026년 2월 정산"
            String settlementDate,  // ex: "2026-03-10"
            BigDecimal amount,
            String status           // PENDING / COMPLETED
    ) {}
}
