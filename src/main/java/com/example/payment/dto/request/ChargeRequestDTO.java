// src/main/java/com/example/payment/dto/request/ChargeRequestDTO.java
package com.example.payment.dto.request;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * [결제 충전 요청 DTO]
 * 사용자가 충전을 시도할 때 전달하는 요청 파라미터를 담는 객체.
 * 프론트엔드 API 호출 또는 메시지 큐(RabbitMQ)를 통한 수신 시 데이터 운반체로 사용됨.
 */
@Getter
@NoArgsConstructor
@ToString
public class ChargeRequestDTO {

    /** * 충전 요청자 고유 식별자 (Member ID)
     * 결제 결과를 반영할 대상 지갑(Wallet)을 식별하는 데 사용됨.
     */
    private Long memberId;

    /** * 충전 요청 금액 
     * 금융 데이터의 정확성을 위해 부동 소수점 오차가 없는 BigDecimal 타입 사용.
     */
    private BigDecimal amount;

    /** * 결제 수단 코드
     * 클라이언트 또는 외부 시스템으로부터 소문자(ex: "kakaopay")로 수신됨.
     * 서비스 레이어에서 .toUpperCase() 변환 후 Enum(PayType) 검증을 거쳐 처리됨. 
     * 예: "KAKAOPAY", "NAVERPAY", "BANK_TRANSFER" 등 PG사 연동 시 구분자로 활용.
     */
    private String payType;
}