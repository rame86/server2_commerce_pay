package com.example.payment.dto.event;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RabbitMQ 메시지 큐로 발행할 결제 이벤트 DTO
 * (Jackson 라이브러리를 통한 JSON 직렬화/역직렬화 대상)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 역직렬화를 위한 기본 생성자 (무분별한 객체 생성 방지를 위해 protected)
@AllArgsConstructor
public class PaymentEventDto {

    // 1. 이벤트 메타 데이터
    private String eventId;          // 이벤트 고유 식별자 (MSA 환경에서 메시지 중복 처리 방지/멱등성 보장용)
    private LocalDateTime createdAt; // 이벤트 발생 일시

    // 2. 비즈니스 데이터
    private Long paymentId;          // 결제 ID
    private String status;           // 결제 상태 (ex: "COMPLETED", "FAILED")

    /**
     * Service 계층에서 객체 생성 시 사용하는 커스텀 생성자
     * 비즈니스 데이터만 넘겨받고, 메타 데이터(eventId, createdAt)는 자동 생성되도록 처리
     */
    public PaymentEventDto(Long paymentId, String status) {
        this.eventId = UUID.randomUUID().toString();
        this.paymentId = paymentId;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }
}