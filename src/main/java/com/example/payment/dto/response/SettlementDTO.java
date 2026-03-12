// src/main/java/com/example/payment/dto/response/SettlementDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

/**
 * 정산 내역 데이터를 담는 DTO
 * settlement.ledgers 테이블 구조 반영
 */
@Builder
public record SettlementDTO(
    UUID ledgerId,              // 정산 내역 고유 ID
    Long artistId,              // 아티스트 ID
    String orderId,             // 관련 주문 ID
    String revenueType,         // 수익 발생 유형 (MEMBERSHIP, TICKET, DONATION)
    BigDecimal grossAmount,     // 상품 원가
    BigDecimal feeAmount,       // 플랫폼 수수료
    BigDecimal netAmount,       // 아티스트 실제 정산액
    String status,              // 정산 상태 (COMPLETED, REFUNDED)
    String eventTitle,          // 내역서 표기용 상세 내용
    OffsetDateTime createdAt    // 생성 일시
) {}