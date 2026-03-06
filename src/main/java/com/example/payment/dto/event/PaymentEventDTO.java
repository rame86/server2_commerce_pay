// src/main/java/com/example/payment/dto/event/PaymentRequestDTO.java
package com.example.payment.dto.event;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// RabbitMQ를 통해 전달받는 결제 요청 데이터 포장지

@Getter
@NoArgsConstructor // Jackson 컨버터가 JSON 역직렬화를 할 때 기본 생성자가 반드시 필요.
@ToString // 수신 확인용 로그를 찍을 때 객체 안의 값을 보기 위해 추가.
public class PaymentEventDTO {

    // JSON의 Key 값과 변수명이 정확히 일치해야 자동 매핑.
    private String orderId; // 주문아이디
    private Long memberId; // 결제자 member_id (지갑 조회를 위해 필수)
    private BigDecimal amount; // 총 결제 혹은 환불 요청 금액
    private String type; // 요청 타입 (결제 or 환불 or 후원)
    private String eventTitle; // 결제 상세 내역에 기록 내용(공연명 or 상품명 or 후원내역)
    private Long artistId;
    private String replyRoutingKey; // 요청한 서비스가 응답받길 원하는 라우팅 키

    public String getReservationId() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
        
}