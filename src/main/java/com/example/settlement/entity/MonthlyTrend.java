package com.example.settlement.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [월별 통계 엔티티]
 * 어드민 대시보드 트렌드 그래프를 위해 월별 총 거래액과 수수료 수익을 별도로 집계함.
 */
@Entity
@Getter
@Table(name = "monthly_stats", schema = "settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MonthlyTrend {

    /** 고유 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** * 월 (YYYY-MM 형식)
     * 예: "2024-03"
     */
    @Column(name = "stat_month", nullable = false, unique = true, columnDefinition = "bpchar(7)")
    private String month;

    /** 월별 총 거래액 (Gross Amount 합계) */
    @Column(name = "total_gross_amount", nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal totalGross = BigDecimal.ZERO;

    /** 월별 총 수수료 (Fee Amount 합계) */
    @Column(name = "total_fee_amount", nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal totalFee = BigDecimal.ZERO;

    /** 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /** 최종 업데이트 시각 */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * [비즈니스 로직: 누적 업데이트]
     * 새로운 결제/환불 발생 시 해당 월의 합계에 반영함.
     */
    public void addAmounts(BigDecimal gross, BigDecimal fee) {
        this.totalGross = this.totalGross.add(gross != null ? gross : BigDecimal.ZERO);
        this.totalFee = this.totalFee.add(fee != null ? fee : BigDecimal.ZERO);
    }
}