// src/main/java/com/example/payment/dto/response/ArtistSettlementRowDTO.java
package com.example.admin.dto.admin.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * [아티스트별 정산 목록 행 DTO]
 * 관리자 대시보드의 리스트 영역에서 개별 아티스트의 정산 지표를 표현함.
 */
public record ArtistSettlementRowDTO(
    /** 아티스트 식별자 */
    Long artistId,

    /** 아티스트 활동명 */
    String artistName,           

    /** 해당 아티스트가 발생시킨 총 매출액 (수수료 차감 전) */
    BigDecimal grossAmount,

    /** 플랫폼이 징수한 총 수수료 금액 */
    BigDecimal feeAmount,

    /** 아티스트에게 실제로 돌아갈 순 정산 금액 (Gross - Fee) */
    BigDecimal netAmount,

    /** 현재 정산 진행 상태 (예: PENDING: 대기, COMPLETED: 완료, HOLD: 보류) */
    String status,

    /** 해당 아티스트의 가장 최근 거래(결제/환불) 발생 시각 */
    OffsetDateTime lastTransactionDate
) {}