// src/main/java/com/example/payment/dto/event/PaymentEventDTO.java
package com.example.payment.dto.event;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class PaymentEventDTO {

    /* ==========================================
     * [이벤트 타입별 필수 Payload 규격]
     * * 1. 결제 (PAYMENT) 
     * - 공연/상품: orderId, memberId, artistId, amount, originalAmount, quantity, fee, type, eventTitle, replyRoutingKey
     * - 후원: orderId, memberId, artistId, amount, type, eventTitle, replyRoutingKey
     * * 2. 환불 (REFUND)
     * - 공통: orderId, memberId, artistId, amount, type, replyRoutingKey
     * * 3. 정산 조회 (SETTLEMENT)
     * - 공통: artistId, type, replyRoutingKey
     * ========================================== */

    // === [공통 및 식별 정보] ===
    private String type;             // 요청 타입 (PAYMENT, REFUND, DONATION, SETTLEMENT)
    private String orderId;          // 주문/예약 번호 (환불 시 원본 결제건 조회용)
    private Long memberId;           // 사용자 ID
    private String replyRoutingKey;  // 응답받을 라우팅 키

    // === [금액 및 수량 정보] ===
    private BigDecimal amount;         // 실제 결제/환불 변동 금액
    private BigDecimal originalPrice; // 원가 (할인 전 금액)
    private Integer quantity;          // 구매/예매 수량
    private BigDecimal fee;            // 플랫폼 수수료(퍼센트)
    private BigDecimal shippingFee;    // 배송비

    // === [메타 데이터] ===
    private Long artistId;           // 후원 대상 아티스트 또는 정산 대상 ID
    private String eventTitle;       // 거래 내역에 기록될 상세 내용 (공연명, 상품명 등)
}

