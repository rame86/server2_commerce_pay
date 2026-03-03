// src/main/java/com/example/payment/dto/response/PaymentResponseDTO.java
package com.example.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDTO {
    // 요청 id
    private String orderId;

    // 결제 상태: PROCESSING, COMPLETE, FAIL 등
    private String status;

    // 결과 메시지: 성공, 실패 사유 등 상세 정보
    private String message;
}