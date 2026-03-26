// src/main/java/com/example/payment/dto/response/SettlementDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;

/**
 * [정산 상세 내역 DTO]
 * 아티스트별 수익 발생 원천과 수수료 구조를 포함한 정산 명세 데이터.
 * 정산 시스템의 무결성을 보장하기 위해 모든 금액을 BigDecimal로 관리함.
 */
@Builder
public record SettlementDTO(
    /** 정산 항목 고유 식별자 (UUID) */
    UUID ledgerId,              

    /** 정산금을 수령할 아티스트 식별 ID */
    Long artistId,              

    /** * [참조] 원본 주문 번호. 
     * 중복 정산을 방지하고 결제-정산 데이터를 매핑하는 핵심 키.
     */
    String orderId,             

    /** 수익 발생 유형 (MEMBERSHIP: 멤버십, TICKET: 티켓, DONATION: 후원 등) */
    String revenueType,         

    /** 상품 판매 원가 (플랫폼 수수료 차감 전 금액) */
    BigDecimal grossAmount,     

    /** 플랫폼이 징수한 서비스 수수료 금액 */
    BigDecimal feeAmount,       

    /** * [핵심] 아티스트에게 실제로 지급될 최종 정산액.
     * 로직상 grossAmount - feeAmount와 항상 일치해야 함.
     */
    BigDecimal netAmount,       

    /** 정산 처리 상태 (COMPLETED: 정산완료, REFUNDED: 환불로 인한 무효화) */
    String status,              

    /** 사용자 및 아티스트용 내역서에 표기될 상세 제목 (예: "2026 월드투어 서울 티켓") */
    String eventTitle,          

    /** 정산 원장이 생성된 일시 */
    OffsetDateTime createdAt    
) {}

/*데이터 예시
{
  "ledgerId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "artistId": 42,
  "orderId": "ORD-2026-X88",
  "revenueType": "TICKET",
  "grossAmount": 110000.00,
  "feeAmount": 11000.00,
  "netAmount": 99000.00,
  "status": "COMPLETED",
  "eventTitle": "Summer Festival 2026 - Standing A",
  "createdAt": "2026-03-26T15:48:20+09:00"
}

*/