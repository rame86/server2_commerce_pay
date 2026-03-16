// src/main/java/com/example/payment/dto/request/ChargeRequestDTO.java
package com.example.payment.dto.request;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// RabbitMQ를 통해 전달받는 결제 요청 데이터 포장지

@Getter
@NoArgsConstructor // Jackson 컨버터가 JSON 역직렬화를 할 때 기본 생성자가 반드시 필요.
@ToString // 수신 확인용 로그를 찍을 때 객체 안의 값을 보기 위해 추가.
public class ChargeRequestDTO {

    private Long memberId; // 충전요청 member_id (지갑 조회를 위해 필수)
    private BigDecimal amount; // 충전 요청 금액
    private String payType; // "kakao_pay", "naver_pay", "bank_transfer" 등
}
