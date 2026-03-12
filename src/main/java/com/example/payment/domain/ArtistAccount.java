package com.example.payment.domain;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "artist_accounts", schema = "settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA를 위한 기본 생성자
public class ArtistAccount {

    @Id
    @Column(name = "artist_id")
    private Long artistId;

    @Column(name = "total_balance", nullable = false)
    private BigDecimal totalBalance;

    @Column(name = "withdrawable_balance", nullable = false)
    private BigDecimal withdrawableBalance;

    @Version
    private Integer version;

    @Builder // 필요한 필드만 받는 생성자에 빌더 적용
    public ArtistAccount(Long artistId, BigDecimal totalBalance, BigDecimal withdrawableBalance) {
        this.artistId = artistId;
        // 빌더에서 값을 안 주면 null이 들어오는 것을 방지 (기본값 설정)
        this.totalBalance = totalBalance != null ? totalBalance : BigDecimal.ZERO;
        this.withdrawableBalance = withdrawableBalance != null ? withdrawableBalance : BigDecimal.ZERO;
    }

    public void addBalances(BigDecimal amount) {
        this.totalBalance = this.totalBalance.add(amount);
        this.withdrawableBalance = this.withdrawableBalance.add(amount);
    }
}