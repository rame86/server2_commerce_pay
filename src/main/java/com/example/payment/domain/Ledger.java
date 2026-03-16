// src/main/java/com/example/payment/domain/Ledger.java
package com.example.payment.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "ledgers", schema = "settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ledger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ledger_id", updatable = false, nullable = false)
    private UUID ledgerId;

    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    @Column(name = "order_id", nullable = false, unique = true) // 중복 정산 방지 제약조건
    private String orderId;

    @Column(name = "revenue_type", nullable = false)
    private String revenueType; // MEMBERSHIP, TICKET, DONATION 등

    @Column(name = "gross_amount", nullable = false)
    private BigDecimal grossAmount; // 상품 원가

    @Column(name = "fee_amount", nullable = false)
    private BigDecimal feeAmount; // 플랫폼 수수료

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount; // 아티스트 실제 정산액

    @Column(nullable = false)
    private String status; // COMPLETED, REFUNDED 등

    @Column(name = "event_title")
    private String eventTitle; // 내역서 표기용 상세 내용

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public Ledger(Long artistId, String orderId, String revenueType, BigDecimal grossAmount,
                  BigDecimal feeAmount, BigDecimal netAmount, String status, String eventTitle) {
        this.artistId = artistId;
        this.orderId = orderId;
        this.revenueType = revenueType;
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.netAmount = netAmount;
        this.status = status;
        this.eventTitle = eventTitle;
    }

    // 환불 시 상태 변경용
    public void markAsRefunded() {
        this.status = "REFUNDED";
    }
}