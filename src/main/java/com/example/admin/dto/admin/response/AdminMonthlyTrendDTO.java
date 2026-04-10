package com.example.admin.dto.admin.response;


import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어드민 대시보드용 월별 트렌드 모음 DTO (최근 6개월)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AdminMonthlyTrendDTO {

    // 최근 6개월간의 월별 통계 리스트 (MonthlyTrendDTO 목록)
    private List<MonthlyTrendDTO> trends;

    /**
     * 리스트를 반환하는 메서드 (요청하신 aaa() 형태의 게터 역할)
     */
    public List<MonthlyTrendDTO> getMonthlyTrends() {
        return this.trends;
    }
}