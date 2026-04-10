// src/main/java/com/example/payment/service/Event/TransactionEventService.java
package com.example.payment.service.Event;

import com.example.payment.dto.event.PaymentEventRequestDTO;

public interface TransactionEventService {

        /**
     * [이벤트 초기 상태 기록]
     * 비즈니스 로직 시작 전 무조건 PENDING 상태로 커밋.
     */    
    public void createPendingEvent(PaymentEventRequestDTO dto);
    
    /**
     * [이벤트 결과 업데이트]
     * 메인 트랜잭션 롤백 시에도 COMPLETE/FAIL 상태는 별도 커밋되도록 보장.
     */    
    public void updateEventStatus(String orderId, String status);
}