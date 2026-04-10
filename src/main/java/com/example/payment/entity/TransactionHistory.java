// src/main/java/com/example/payment/entity/TransactionHistory.java
package com.example.payment.entity;

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
 * [거래 이력 원장 엔티티]
 * 결제, 충전, 환불, 후원 등 모든 자산 변동 내역을 기록함.
 * 데이터 무결성을 위해 수정이 불가능한 '불변(Immutable)' 상태로 관리됨.
 */
@Entity
@Table(
    name = "transactions", 
    schema = "pay",
    // 성능 최적화: 사용자별 결제 내역 조회 시 최신순 정렬을 위한 복합 인덱스 설정
    indexes = @Index(name = "idx_wallet_created", columnList = "wallet_id, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 기본 생성자 (외부 생성 제한)
@AllArgsConstructor
@Builder
public class TransactionHistory {

    // === [거래 식별 및 참조] ===
    /** 거래 고유 식별자 (UUID 기반) */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID transactionId;

    /** 자산 변동이 발생한 대상 지갑 ID */
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    /** 원본 주문 번호 또는 PG사 결제 고유 번호 (추적용 참조 ID) */
    @Column(name = "reference_id", nullable = false)
    private String referenceId; 

    // === [거래 분류] ===
    /** 거래 유형 (PAYMENT: 결제, CHARGE: 충전, REFUND: 환불, DONATION: 후원) */
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType; 

    // === [금액 상세 및 정산 정보] ===
    /** 실제 지갑 잔액의 변동 수치 (+/-) */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount; 

    /** 할인 전 상품 원가 (정산 및 통계 데이터 산출용) */
    @Column(name = "original_price", precision = 15, scale = 2)
    private BigDecimal originalAmount; 

    /** 적용된 플랫폼 수수료 (정산 시 증빙 데이터로 활용) */
    @Column(name = "fee", precision = 15, scale = 2)
    private BigDecimal fee; 
    
    /** 물류 배송 비용 (상품 주문 시 사용) */
    @Column(name = "shipping_fee", precision = 15, scale = 2)
    private BigDecimal shippingFee; 

    /** 구매 또는 예매 수량 */
    @Column(name = "quantity")
    private Integer quantity; 

    /** * [거래 후 잔액 스냅샷]
     * 거래 직후의 지갑 잔액을 기록함.
     * 과거 내역을 합산하지 않고도 특정 시점의 잔액 무결성을 검증하는 용도.
     */
    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter; 

    // === [커머스 및 대상 정보] ===
    /** 후원 대상 아티스트 또는 상품 판매자(아티스트) 식별자 */
    @Column(name = "artist_id")
    private Long artistId; 

    /** 사용자에게 보여질 거래 상세 메모 (예: '포인트 충전', '공연 예매 완료') */
    @Column(name = "description")
    private String description; 

    // === [기록 정보] ===
    /** 거래 기록이 생성된 시각 (INSERT 시 자동 기록) */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

}