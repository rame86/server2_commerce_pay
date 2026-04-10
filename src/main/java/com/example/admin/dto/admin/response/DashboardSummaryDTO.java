// src/main/java/com/example/payment/dto/response/DashboardSummaryDTO.java
package com.example.admin.dto.admin.response;

import java.math.BigDecimal;

/**
 * [대시보드 상단 요약 통계 DTO]
 * 플랫폼 전체의 재무 흐름을 수치화하여 보여줌.
 */
public record DashboardSummaryDTO(
    /** 플랫폼 내에서 발생한 총 매출 합계 (수수료 차감 전 원가 총합) */
    BigDecimal totalGrossAmount,      
    
    /** 플랫폼이 취득한 총 수수료 수익 합계 */
    BigDecimal totalPlatformFee,      
    
    /** 아티스트에게 지급해야 할 정산 예정액 (Gross - Fee 중 미지급분) */
    BigDecimal totalExpectedAmount,   
    
    /** 아티스트에게 지급(출금) 처리가 완료된 금액 합계 */
    BigDecimal totalSettledAmount     
) {}