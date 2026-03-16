// src/main/java/com/example/payment/dto/response/PaymentResponseDTO.java
package com.example.payment.dto.event;

/**
 * 결제 응답 데이터 객체 (Record 활용)
 */
public record PaymentResponseDTO<T>(
    String orderId,    // 요청 ID
    String status,     // 결제 상태: PROCESSING, COMPLETE, FAIL 등
    String message,    // 결과 메시지: 성공, 실패 사유 등 상세 정보
    String type,        // 요청 시 타입
    T payload // List<SettlementDTO> 등이 담길 필드
) {
}