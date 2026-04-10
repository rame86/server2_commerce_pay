// src/main/java/com/example/payment/entity/Charge.java
package com.example.payment.entity;

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
import lombok.extern.slf4j.Slf4j;

/**
 * [결제 충전 원장 엔티티]
 * 외부 PG사 결제 요청부터 최종 결과까지의 생명주기를 기록함.
 * 데이터 무결성을 위해 상태 전이 메서드 내부에 로그를 포함함.
 */
@Slf4j
@Entity
@Table(name = "charges", schema = "pay")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Charge {

    @Id
    @Column(name = "charge_id", columnDefinition = "BINARY(16)")
    private UUID chargeId; // 충전 요청 고유 식별값 (UUID)

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId; // 충전액이 반영될 대상 지갑 ID

    @Column(name = "pg_provider")
    private String pgProvider; // PG사 구분 (KAKAOPAY, NAVERPAY 등)

    @Column(name = "pg_transaction_id")
    private String pgTransactionId; // PG사 발급 거래 번호 (TID 등)

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount; // 충전 요청 금액 (금융 정밀도 확보)

    @Column(name = "status", nullable = false)
    private String status; // 처리 상태 (PENDING: 대기, SUCCESS: 성공, FAIL: 실패)

    @Column(name = "error_message")
    private String errorMessage; // 실패 시 PG사 또는 시스템에서 반환한 사유

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt; // 최초 요청 시각

    @Column(name = "completed_at")
    private OffsetDateTime completedAt; // 최종 승인 또는 실패 처리 시각

    /**
     * [PG 거래 식별자 업데이트]
     * PG사 결제 준비(Ready) 단계에서 발급받은 TID를 원장에 매핑함.
     */
    public void updateTid(String tid) {
        log.info(">>> [CHARGE_TID_UPDATE] TID 업데이트 - ChargeId: {}, TID: {}", this.chargeId, tid);
        this.pgTransactionId = tid;
    }

    /**
     * [비즈니스 로직: 결제 승인 완료]
     * PG사로부터 승인 응답을 받은 후 상태를 SUCCESS로 변경하고 완료 시각을 기록함.
     */
    public void success() {
        log.info(">>> [CHARGE_SUCCESS] 결제 원장 성공 처리 - ChargeId: {}, Amount: {}", this.chargeId, this.amount);
        this.status = "SUCCESS";
        this.completedAt = OffsetDateTime.now();
    }

    /**
     * [비즈니스 로직: 결제 실패 처리]
     * 결제 과정 중 에러 발생 시 상태를 FAIL로 변경하고 구체적인 사유를 기록함.
     */
    public void fail(String errorMessage) {
        log.error(">>> [CHARGE_FAIL] 결제 원장 실패 처리 - ChargeId: {}, Reason: {}", this.chargeId, errorMessage);
        this.status = "FAIL";
        this.errorMessage = errorMessage;
        this.completedAt = OffsetDateTime.now();
    }
}