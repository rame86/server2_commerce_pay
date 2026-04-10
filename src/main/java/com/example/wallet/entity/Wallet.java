// src/main/java/com/example/wallet/domain/Wallet.java
package com.example.wallet.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [사용자 지갑 엔티티]
 * 사용자의 잔액 상태를 관리하며, 포인트 변동 시 동시성 제어를 위해 낙관적 락을 적용함.
 * 'pay' 스키마 내 'wallets' 테이블에 매핑됨.
 */
@Slf4j
@Entity
@Table(name = "wallets", schema = "pay")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 무분별한 객체 생성 방지
@AllArgsConstructor
@Builder
public class Wallet {

    // === [식별 정보] ===
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)")
    private UUID walletId; // 지갑 고유 식별자 (UUID)

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId; // Core 서비스의 회원 PK와 연동되는 식별자

    // === [금액 정보] ===
    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance; // 실시간 잔액 (금융 정밀도 확보를 위해 BigDecimal 사용)

    // === [지갑 활성 상태] ===
    @Column(name = "status", length = 20)
    private String status; // ACTIVE, INACTIVE 등 지갑의 상태

    // === [동시성 및 보안] ===
    @Version
    private Integer version; // JPA Optimistic Lock: 포인트 차감 시 데이터 정합성 보장

    // =======================================================
    // [비즈니스 로직]
    // 숫자가 민감한 도메인 특성상 외부에서 Setter를 쓰지 못하게 막고,
    // 오직 검증된 addBalance, deductBalance를 통해서만 상태를 바꾸도록 제한
    // =======================================================

    /**
     * [잔액 가산]
     * 충전 또는 이벤트 적립 시 지갑의 잔액을 증가시킴.
     * @param amount 가산할 금액
     */
    public void addBalance(BigDecimal amount) {
        log.info(">>> [WALLET_ADD] 잔액 가산 시작 - WalletId: {}, Amount: {}",
        this.walletId, amount);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn(">>> [WALLET_ADD] 유효하지 않은 가산 금액: {}", amount);
            return;
        }

        this.balance = this.balance.add(amount);
        log.info(">>> [WALLET_ADD] 잔액 가산 완료 - New Balance: {}",
        this.balance);
    }

    /**
     * [잔액 차감]
     * 상품 구매 또는 출금 시 지갑의 잔액을 감소시킴.
     * @param amount 차감할 금액
     * @throws IllegalStateException 잔액이 부족할 경우 예외 발생
     */
    public void deductBalance(BigDecimal amount) {
        log.info(">>> [WALLET_DEDUCT] 잔액 차감 시작 - WalletId: {}, Amount: {}",
        this.walletId, amount);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn(">>> [WALLET_DEDUCT] 유효하지 않은 차감 금액: {}", amount);
            return;
        }

        // 잔액 부족 검증: 차감액이 현재 잔액보다 큰 경우 예외 처리
        if (this.balance.compareTo(amount) < 0) {
            log.error(">>> [WALLET_DEDUCT] 잔액 부족 실패 - Current: {}, Request: {}",
             this.balance, amount);
            throw new IllegalStateException("잔액이 부족합니다. (현재 잔액: " + this.balance + ")");
        }

        this.balance = this.balance.subtract(amount);
        log.info(">>> [WALLET_DEDUCT] 잔액 차감 완료 - New Balance: {}", this.balance);
    }
}