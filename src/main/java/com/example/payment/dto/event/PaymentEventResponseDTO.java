// src/main/java/com/example/payment/dto/response/PaymentEventResponseDTO.java
package com.example.payment.dto.event;

/**
 * [결제 이벤트 응답 DTO]
 * 결제 이벤트 처리 결과를 담는 데이터 전송 객체
 * @param <T> 페이로드 데이터 타입 (제네릭)
 * @param orderId 주문 고유 번호
 * @param status  처리 상태 (SUCCESS, FAILED 등)
 * @param message 처리 결과 메시지
 * @param type    이벤트 유형 식별자
 * @param payload 추가 데이터 객체 (지갑 잔액, 결제 정보 등)
 */
public record PaymentEventResponseDTO<T>(
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
