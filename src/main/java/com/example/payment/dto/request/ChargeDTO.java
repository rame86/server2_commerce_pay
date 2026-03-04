// src/main/java/com/example/payment/dto/request/ChargeDTO.java
package com.example.payment.dto.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본 생성자 접근 제어로 안정성 확보
@AllArgsConstructor
public class ChargeDTO {

    // 충전 요청 고유 ID
    private UUID chargeId;

    // 충전 대상 지갑 ID
    private UUID walletId;

    // PG사 이름 (KAKAOPAY, NAVERPAY 등)
    private String pgProvider;

    // PG사 연동 거래 번호 (TID 등)
    private String pgTransactionId;

    // 충전 요청 금액
    private BigDecimal amount;

    // 처리 상태 (PENDING, SUCCESS, FAILED)
    private String status;

    // 실패 시 에러 메시지
    private String errorMessage;

    // 요청 시각
    private OffsetDateTime createdAt;

    // 완료 시각
    private OffsetDateTime completedAt;

    // 성공 로직
    public void success() {
        this.status = "SUCCESS";
        this.completedAt = OffsetDateTime.now();
    }

    // 실패 로직
    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.completedAt = OffsetDateTime.now();
    }
}