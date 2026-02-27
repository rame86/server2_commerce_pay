// src/main/java/com/example/payment/dto/response/WalletResponseDTO.java
package com.example.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletResponseDTO {
    private String walletId;
    private Long memberId;
    private BigDecimal balance; // NUMERIC 매핑을 위해 BigDecimal 사용
    private String status;
    private Integer version;
    private OffsetDateTime createdAt; // 타입 변경
    private OffsetDateTime updatedAt; // 타입 변경

}