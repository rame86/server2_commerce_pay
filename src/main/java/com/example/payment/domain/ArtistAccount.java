// src/main/java/com/example/payment/domain/ArtistAccount.java
package com.example.payment.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [아티스트 정산 계좌 엔티티]
 * 아티스트별 총 수익 및 출금 가능 잔액을 관리함.
 * 별도의 'settlement' 스키마를 사용하여 도메인을 격리함.
 */
@Entity
@Getter
@Table(name = "artist_accounts", schema = "settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 스펙 준수 및 무분별한 외부 생성 방지
public class ArtistAccount {

    /** 아티스트 식별자 (PK) */
    @Id
    @Column(name = "artist_id")
    private Long artistId;

    /** 누적 총 수익금 (플랫폼 수수료 제외 전/후 비즈니스 로직에 따라 결정) */
    @Column(name = "total_balance", nullable = false)
    private BigDecimal totalBalance;

    /** 현재 출금 가능한 실제 잔액 */
    @Column(name = "withdrawable_balance", nullable = false)
    private BigDecimal withdrawableBalance;

    /** * [낙관적 락(Optimistic Lock)] 
     * 정산 시 발생할 수 있는 데이터 경합(Race Condition)을 방지하기 위한 버전 관리 필드.
     * 여러 프로세스가 동시에 잔액을 수정할 경우 충돌을 감지하여 데이터 무결성을 보장함.
     */
    @Version
    private Integer version;

    /** 레코드 생성 시점 (Audit) */
    @CreationTimestamp 
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /** 레코드 마지막 수정 시점 (Audit) */
    @UpdateTimestamp 
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * [엔티티 빌더 생성자]
     * 객체 생성 시 필수 필드에 대한 기본값을 보장함.
     */
    @Builder 
    public ArtistAccount(Long artistId, BigDecimal totalBalance, BigDecimal withdrawableBalance) {
        this.artistId = artistId;
        // 금융 데이터 누락 방지를 위한 Null-Safe 처리 (기본값 0)
        this.totalBalance = totalBalance != null ? totalBalance : BigDecimal.ZERO;
        this.withdrawableBalance = withdrawableBalance != null ? withdrawableBalance : BigDecimal.ZERO;
    }

    /**
     * [비즈니스 로직: 잔액 가산]
     * 객체지향적 설계(Rich Domain Model)를 위해 엔티티 내부에서 상태 변화를 수행함.
     * @param amount 추가될 정산 금액 (정밀도 유지를 위해 BigDecimal 사용)
     */
    public void addBalances(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가산할 금액은 0보다 커야 함");
        }
        this.totalBalance = this.totalBalance.add(amount);
        this.withdrawableBalance = this.withdrawableBalance.add(amount);
    }
}