// src/main/java/com/example/payment/dto/response/WalletResponseDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor  // JSON 파싱용 기본 생성자
@AllArgsConstructor // 모든 필드 인자 생성자 (에러 해결 핵심!)
public class WalletResponseDTO {
    private String walletId;
    private Long memberId;

    private BigDecimal balance; // NUMERIC 매핑을 위해 BigDecimal 사용

    private String status;
    private Integer version;
    private OffsetDateTime createdAt; // 타입 변경
    private OffsetDateTime updatedAt; // 타입 변경

    // BigDecimal을 받는 생성자 (에러 해결!)
    public WalletResponseDTO(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getBalance() {
        return balance;
    }

// 수정완료
}