package com.example.payment.domain;

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

@Entity
@Table(name = "wallets", schema = "pay")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Wallet {

    // === [식별 정보] ===
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID walletId;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId; // Core 서비스의 회원 식별자 연동

    // === [금액 정보] ===
    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance; // 현재 잔액

    // === [지갑 활성 상태] ===
    private String status;

    // === [동시성 및 보안] ===
    @Version
    private Integer version; // 낙관적 잠금(Optimistic Lock)을 통한 포인트 차감 동시성 제어

    // 비즈니스 로직: BigDecimal 연산 적용
    public void addBalance(BigDecimal amount) {
        if (amount == null)
            return;
        this.balance = this.balance.add(amount);
    }

    public void deductBalance(BigDecimal amount) {
        if (amount == null)
            return;
        // balance < amount 체크
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }
        this.balance = this.balance.subtract(amount);
    }
}