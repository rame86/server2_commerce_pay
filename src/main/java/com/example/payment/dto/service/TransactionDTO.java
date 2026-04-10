// src/main/java/com/example/payment/dto/service/TransactionDTO.java
package com.example.payment.dto.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;

/**
 * [공통 트랜잭션 정보 DTO]
 * 포인트 증감 및 결제 원장 데이터를 클라이언트에 전송하기 위한 불변 객체.
 * 결제 이력 조회 시 개별 거래 행(Row)의 상세 데이터를 담음.
 */
@Builder
public record TransactionDTO(
    /** 거래 기록 고유 ID (내부 추적용 UUID) */
    UUID transactionId,         

    /** 자산 변동이 발생한 지갑 식별자 */
    UUID walletId,               

    /** 거래 유형 (예: CHARGE: 충전, PAYMENT: 결제, REFUND: 환불, DONATION: 후원) */
    String transactionType,     

    /** * [중요] 실제 지갑 잔액의 변동 수치.
     * 충전 시 (+), 결제 시 (-) 등 실제 가산/차감되는 금액.
     */
    BigDecimal amount,          

    /** 할인이나 수수료가 적용되기 전의 순수 상품 원가 */
    BigDecimal originalPrice,   

    /** 해당 거래에서 발생한 플랫폼 수수료율(%) */
    BigDecimal fee,             

    /** 물류 및 배송 비용 (상품 결제 시 사용) */
    BigDecimal shippingFee,     

    /** 구매 또는 예매 수량 */
    Integer quantity,           

    /** 후원 대상 아티스트 또는 상품 판매자 ID */
    Long artistId,               

    /** * [무결성 검증] 거래 직후의 지갑 잔액 스냅샷.
     * 이전 거래 내역을 모두 합산하지 않고도 잔액의 정합성을 즉시 확인할 수 있는 지표.
     */
    BigDecimal balanceAfter,    

    /** 주문 번호 또는 PG사 결제 고유 번호 등 외부 서비스와의 연결 고리 ID */
    String referenceId,         

    /** 사용자에게 노출될 거래 상세 설명 (예: "공연 티켓 예매", "카카오페이 충전") */
    String description,         

    /** 거래 기록이 생성된 시각 (ISO-8601 형식) */
    OffsetDateTime createdAt    
) {}

/*
데이터 예시:
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "walletId": "f47ac10b-58cc-4372-a567-0e02b2c3d4e5",
  "transactionType": "PAYMENT",
  "amount": -55000.00,
  "originalPrice": 60000.00,
  "fee": 20,
  "balanceAfter": 145000.00,
  "description": "아티스트 공식 응원봉 외 1건 구매",
  "referenceId": "ORD-20260326-X11",
  "createdAt": "2026-03-26T15:53:10+09:00"
}
*/