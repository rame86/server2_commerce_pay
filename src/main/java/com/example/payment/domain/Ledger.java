// src/main/java/com/example/payment/domain/Ledger.java
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [정산 원장 엔티티]
 * 거래별 매출액, 수수료, 최종 정산액을 기록하는 상세 명세서임.
 * 결제 서비스와 분리된 'settlement' 스키마에서 관리됨.
 */
@Entity
@Getter
@Table(name = "ledgers", schema = "settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ledger {

    /** 정산 항목 고유 식별자 (UUID 자동 생성) */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ledger_id", updatable = false, nullable = false)
    private UUID ledgerId;

    /** 정산금을 수령할 아티스트 ID */
    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    /** * 원본 주문 ID. 
     * 중복 정산을 방지하기 위해 데이터베이스 수준에서 Unique 제약조건 설정(멱등성 보장). 
     */
    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    /** 매출 유형 (MEMBERSHIP: 멤버십, TICKET: 티켓, DONATION: 후원 등) */
    @Column(name = "revenue_type", nullable = false)
    private String revenueType;

    /** 총 매출액 (상품의 판매 원가) */
    @Column(name = "gross_amount", nullable = false)
    private BigDecimal grossAmount;

    /** 플랫폼 수수료 금액 (플랫폼이 취하는 이득) */
    @Column(name = "fee_amount", nullable = false)
    private BigDecimal feeAmount;

    /** 아티스트 실제 정산액 (Gross - Fee 결과값) */
    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    /** 정산 상태 (COMPLETED: 완료, REFUNDED: 환불됨) */
    @Column(nullable = false)
    private String status;

    /** 내역서 및 대시보드 표기용 상세 제목 (공연명 등) */
    @Column(name = "event_title")
    private String eventTitle;

    /** 정산 원장 생성 시각 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /**
     * [엔티티 빌더]
     * 정산 계산 로직이 완료된 후 불변 객체 형태로 저장하기 위해 활용.
     */
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

    /**
     * [비즈니스 로직: 환불 상태 변경]
     * 결제 취소 또는 환불 발생 시 해당 정산 건의 상태를 무효화(REFUNDED)함.
     */
    public void markAsRefunded() {
        this.status = "REFUNDED";
    }
}