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
import lombok.extern.slf4j.Slf4j;

/**
 * [아티스트 정산 계좌 엔티티]
 * 아티스트별 총 수익 및 출금 가능 잔액을 관리함.
 * 별도의 'settlement' 스키마를 사용하여 결제 도메인과 물리적으로 격리함.
 */
@Slf4j
@Entity
@Getter
@Table(name = "artist_accounts", schema = "settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistAccount {

    /** 아티스트 식별자 (PK) */
    @Id
    @Column(name = "artist_id")
    private Long artistId;

    /** 누적 총 수익금 (플랫폼 수수료 제외 후 확정된 총액) */
    @Column(name = "total_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalBalance;

    /** 현재 출금 가능한 실제 잔액 (정산 완료 건 중 미출금액) */
    @Column(name = "withdrawable_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal withdrawableBalance;

    /** * [낙관적 락(Optimistic Lock)] 
     * 동시 다발적인 정산/출금 요청 시 데이터 경합(Race Condition)을 방지함.
     * 수정 시점에 버전이 다를 경우 예외를 발생시켜 데이터 무결성을 보장함.
     */
    @Version
    private Integer version;

    /** 생성 시각 (Audit) */
    @CreationTimestamp 
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /** 수정 시각 (Audit) */
    @UpdateTimestamp 
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * [엔티티 빌더 생성자]
     * 객체 초기 생성 시 필수 필드에 대해 Null-Safe 로직(BigDecimal.ZERO)을 적용함.
     */
    @Builder 
    public ArtistAccount(Long artistId, BigDecimal totalBalance, BigDecimal withdrawableBalance) {
        log.info(">>> [ARTIST_ACCOUNT_INIT] 계좌 객체 생성 - ArtistId: {}", artistId);
        this.artistId = artistId;
        this.totalBalance = totalBalance != null ? totalBalance : BigDecimal.ZERO;
        this.withdrawableBalance = withdrawableBalance != null ? withdrawableBalance : BigDecimal.ZERO;
    }

    /**
     * [비즈니스 로직: 잔액 업데이트]
     * 정산 확정 시 누적 수익과 출금 가능액을 동시에 가산함.
     * 환불 처리를 위해 음수 값도 허용하도록 유연하게 설계됨.
     * @param amount 반영할 정산 금액 (정수는 가산, 부수는 차감)
     */
    public void addBalances(BigDecimal amount) {
        log.info(">>> [ARTIST_ACCOUNT_UPDATE] 잔액 변동 시도 - ArtistId: {}, 변동액: {}", this.artistId, amount);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            log.warn(">>> [ARTIST_ACCOUNT_UPDATE] 변동액이 0이거나 Null이므로 무시됨.");
            return;
        }

        // BigDecimal은 불변 객체이므로 연산 결과를 다시 할당함
        this.totalBalance = this.totalBalance.add(amount);
        this.withdrawableBalance = this.withdrawableBalance.add(amount);

        log.info(">>> [ARTIST_ACCOUNT_UPDATE] 잔액 반영 완료 - 총수익: {}, 출금가능액: {}", 
                 this.totalBalance, this.withdrawableBalance);
    }
}