// src/main/java/com/example/payment/dto/response/PointHistoryDTO.java
package com.example.admin.dto.admin.response;

import lombok.Builder;

/**
 * [포인트 변동 이력 DTO]
 * 특정 지갑의 포인트 증감 내역을 관리자 또는 사용자에게 보여주기 위한 객체.
 * 단순 변동액뿐만 아니라 거래 후 잔액을 포함하여 데이터 신뢰성을 확보함.
 */
@Builder
public record PointHistoryDTO(
    /** 거래가 확정된 일시 (포맷팅된 문자열) */
    String processedAt,   

    /** 거래 구분 (PAYMENT: 결제, CHARGE: 충전, REFUND: 환불, DONATION: 후원 등) */
    String type,          

    /** * [중요] 변동 금액. 
     * 사용/차감은 마이너스(-), 충전/가산은 플러스(+)로 표시하여 흐름을 직관적으로 표현.
     */
    long amount,          

    /** 거래에 대한 간략한 메모 또는 적요 (예: "포인트 충전", "공연 티켓 예매") */
    String description,   

    /** * [감사용] 거래 직후의 지갑 잔액 스냅샷.
     * 관리자가 전체 이력을 합산하지 않고도 특정 시점의 잔액 정합성을 즉시 검증할 수 있게 함.
     */
    long balanceAfter     
) {}

/*
데이터 예시:
{
  "processedAt": "2026-03-26 15:40:22",
  "type": "PAYMENT",
  "amount": -15000,
  "description": "뮤지컬 '지킬 앤 하이드' 예매",
  "balanceAfter": 35000
}
*/