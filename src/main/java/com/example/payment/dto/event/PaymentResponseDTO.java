// src/main/java/com/example/payment/dto/response/PaymentResponseDTO.java
package com.example.payment.dto.event;

/**
 * [공통 응답 페이로드 객체]
 * * @param <T> 응답 본문에 담길 데이터 타입
 * @param orderId 요청 시 전달받았던 식별 번호
 * @param status  처리 결과 상태
 * @param message 처리 안내 메시지
 */
public record PaymentResponseDTO<T>(
        /** 요청 시 전달받았던 식별 번호 (주문 ID 등) */
        String orderId,

        /** 처리 결과 상태 (SUCCESS: 성공, FAIL: 실패, PROCESSING: 처리 중) */
        String status,

        /** 실패 사유 또는 처리 완료 안내 메시지 */
        String message,

        /** 요청 당시의 이벤트 타입 (분기 처리용) */
        String type,

        /** 실질적인 결과 데이터 (예: 정산 내역 리스트, 지갑 정보 등) */
        T payload) {
}