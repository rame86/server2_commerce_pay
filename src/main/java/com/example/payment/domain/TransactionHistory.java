package com.example.payment.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 결제, 후원, 환불 등 모든 자산 변동 내역을 기록하는 불변(Immutable) 원장
 */

@Entity
@Table(
    name = "transactions", 
    schema = "pay",
    indexes = @Index(name = "idx_wallet_created", columnList = "wallet_id, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TransactionHistory {

    // === [거래 식별 및 참조] ===
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID transactionId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "reference_id", nullable = false)
    private String referenceId; // 주문번호(UUID) 또는 외부 PG 결제 번호 연동

    // === [거래 분류] ===
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType; // PAYMENT(결제), CHARGE(충전), REFUND(환불), DONATION(후원)

    // === [금액 상세 및 정산 정보] ===
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount; // 실제 지갑 변동액 (+/-)

    @Column(name = "original_price", precision = 15, scale = 2)
    private BigDecimal originalAmount; // 상품 원가

    @Column(name = "fee", precision = 15, scale = 2)
    private BigDecimal fee; // 플랫폼 수수료 (정산 시 활용)
    
    @Column(name = "shipping_fee", precision = 15, scale = 2)
    private BigDecimal shippingFee; // 배송비

    @Column(name = "quantity")
    private Integer quantity; // 구매/예매 수량

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter; // 거래 후 잔액 스냅샷 (무결성 검증용)

    // === [커머스 및 대상 정보] ===
    @Column(name = "artist_id")
    private Long artistId; // 후원 대상 또는 상품 판매자 식별자

    @Column(name = "description")
    private String description; // 사용자용 거래 내역 메모

    // === [기록 정보] ===
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

}