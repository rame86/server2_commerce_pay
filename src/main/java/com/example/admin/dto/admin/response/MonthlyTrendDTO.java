package com.example.admin.dto.admin.response;
import java.math.BigDecimal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어드민 통계: 월별 거래 및 수수료 트렌드 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MonthlyTrendDTO {

    // "YYYY-MM" 형태 (예: 2024-03)
    private String month;

    // 월별 총 거래액 (그래프 1 데이터)
    private BigDecimal totalGross;

    // 월별 총 수수료 (그래프 2 데이터)
    private BigDecimal totalFee;

}