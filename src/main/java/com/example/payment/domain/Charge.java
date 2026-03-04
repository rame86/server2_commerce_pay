package com.example.payment.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "charges", schema = "pay")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Charge {

    @Id
    private UUID chargeId;
    
    private UUID walletId;
    private String pgProvider;
    private String pgTransactionId;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    private String status;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;

    public void updateTid(String tid) {
        this.pgTransactionId = tid;
    }

    public void success() {
        this.status = "SUCCESS";
        this.completedAt = OffsetDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = "FAIL";
        this.errorMessage = errorMessage;
        this.completedAt = OffsetDateTime.now();
    }
}