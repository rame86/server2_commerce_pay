// src/main/java/com/example/payment/domain/Charge.java
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

/**
 * [결제 충전 원장 엔티티]
 * 사용자의 충전 요청부터 최종 결과까지의 상태를 기록함.
 * 분산 환경에서의 식별력을 위해 UUID를 PK로 사용하며, 'pay' 스키마에 격리 저장.
 */
@Entity
@Table(name = "charges", schema = "pay")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 무분별한 객체 생성 방지
@AllArgsConstructor
@Builder
public class Charge {

    /** 충전 요청 고유 식별자 (UUID) */
    @Id
    private UUID chargeId;

    /** 충전된 금액이 반영될 대상 지갑 ID */
    private UUID walletId;

    /** 결제를 처리한 PG사 명칭 (예: KAKAOPAY, NAVERPAY) */
    private String pgProvider;

    /** * 외부 PG사에서 발급한 거래 번호 (TID 등).
     * 준비(Ready) 단계에서 발급받아 승인(Approve) 및 취소 시 식별자로 사용.
     */
    private String pgTransactionId;

    /** 충전 요청 금액 (정밀도 19, 소수점 2자리로 금융 데이터 무결성 확보) */
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    /** 처리 상태 (PENDING: 대기, SUCCESS: 성공, FAIL: 실패) */
    private String status;

    /** 결제 실패 시 PG사 또는 시스템에서 반환한 에러 사유 */
    private String errorMessage;

    /** 충전 요청이 생성된 시각 */
    private OffsetDateTime createdAt;

    /** 결제 처리가 최종 완료(성공 또는 실패)된 시각 */
    private OffsetDateTime completedAt;

    /**
     * [TID 업데이트]
     * PG사 결제 준비 응답으로 받은 거래 번호를 원장에 매핑함.
     */
    public void updateTid(String tid) {
        this.pgTransactionId = tid;
    }

    /**
     * [비즈니스 로직: 결제 성공 처리]
     * 상태를 SUCCESS로 변경하고 완료 시각을 기록함.
     */
    public void success() {
        this.status = "SUCCESS";
        this.completedAt = OffsetDateTime.now();
    }

    /**
     * [비즈니스 로직: 결제 실패 처리]
     * 상태를 FAIL로 변경하고 구체적인 실패 사유와 완료 시각을 기록함.
     */
    public void fail(String errorMessage) {
        this.status = "FAIL";
        this.errorMessage = errorMessage;
        this.completedAt = OffsetDateTime.now();
    }
}