// src/main/java/com/example/payment/dto/response/AdminDashboardResponseDTO.java
package com.example.payment.dto.response;

import java.util.List;

import com.example.payment.dto.request.MonthlyTrendDTO;

/**
 * [관리자 대시보드 전체 응답 DTO]
 * 상단 요약 통계(Summary)와 하단 아티스트별 정산 목록을 포함하는 최상위 객체.
 */
public record AdminDashboardResponseDTO(
    /** 전체 정산 지표 요약 (총 거래액, 수수료 등) */
    DashboardSummaryDTO summary, 
    
    /** 아티스트별 상세 정산 행(Row) 리스트 */
    List<ArtistSettlementRowDTO> artistSettlements,

    List<MonthlyTrendDTO> monthlyTrend
) {}

/*
*데이터 예시:
{
  "summary": {
    "totalGrossAmount": 1000000.00,
    "totalPlatformFee": 100000.00,
    "totalExpectedAmount": 700000.00,
    "totalSettledAmount": 200000.00
  },
  "artistSettlements": [
    {
      "artistId": 101,
      "artistName": "아이유",
      "revenueType": "TICKET",
      "netAmount": 450000.00,
      "status": "COMPLETED",
      "updatedAt": "2026-03-26T15:30:00Z"
    },
    {
      "artistId": 102,
      "artistName": "뉴진스",
      "revenueType": "DONATION",
      "netAmount": 250000.00,
      "status": "PENDING",
      "updatedAt": "2026-03-26T16:00:00Z"
    }
  ],
  "monthlyTrend": [
    { "month": "2025-10", "totalGross": 850000.00, "totalFee": 85000.00 },
    { "month": "2025-11", "totalGross": 920000.00, "totalFee": 92000.00 },
    { "month": "2025-12", "totalGross": 1100000.00, "totalFee": 110000.00 },
    { "month": "2026-01", "totalGross": 950000.00, "totalFee": 95000.00 },
    { "month": "2026-02", "totalGross": 980000.00, "totalFee": 98000.00 },
    { "month": "2026-03", "totalGross": 1000000.00, "totalFee": 100000.00 }
  ]
}
*/