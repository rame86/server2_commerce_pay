// src/main/java/com/example/payment/dto/response/TransactionDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;

/**
 * 공통 트랜잭션 정보 DTO
 * 포인트 증감 및 결제 원장 데이터를 전송하기 위한 불변 객체
 */
@Builder
public record TransactionDTO(
    UUID transactionId,         // 거래 기록 고유 ID
    UUID walletId,              // 대상 지갑 ID
    String transactionType,     // 거래 유형 (PAYMENT, REFUND, DONATION 등)
    BigDecimal amount,          // 총 증감액 (+/-)
    BigDecimal originalPrice,   // 결제 원가
    BigDecimal fee,             // 수수료
    BigDecimal shippingFee,     // 배송비
    Integer quantity,           // 수량
    Long artistId,              // 대상 아티스트 ID
    BigDecimal balanceAfter,    // 거래 직후 잔액 (검증용)
    String referenceId,         // 주문번호 등 외부 참조 ID
    String description,         // 거래 상세 설명
    OffsetDateTime createdAt    // 기록 시각
) {}