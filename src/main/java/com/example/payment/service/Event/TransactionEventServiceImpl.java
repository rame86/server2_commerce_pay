// src/main/java/com/example/payment/service/Event/TransctionEventServiceImpl.java
package com.example.payment.service.Event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.payment.entity.TransctionEvent;
import com.example.payment.repository.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventServiceImpl implements TransactionEventService {

    private final ProcessedEventRepository eventRepository;

    /**
     * [이벤트 초기 상태 기록]
     * 비즈니스 로직 시작 전 무조건 PENDING 상태로 커밋.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createPendingEvent(PaymentEventRequestDTO dto) {
        TransctionEvent event = TransctionEvent.builder()
                .orderId(dto.getOrderId())
                .replyRoutingKey(dto.getReplyRoutingKey())
                .status("PENDING")
                .build();
        
        eventRepository.save(event);
        log.info("이벤트 상태 기록 완료 (PENDING) - OrderId: {}", dto.getOrderId());
    }

    /**
     * [이벤트 결과 업데이트]
     * 어떤 상태에서도 COMPLETE/FAIL 상태는 별도 커밋되도록 보장.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateEventStatus(String orderId, String status) {
        eventRepository.findByOrderId(orderId).ifPresentOrElse(
            event -> {
                event.updateStatus(status);
                log.info("이벤트 상태 업데이트 완료 ({}) - OrderId: {}", status, orderId);
            },
            () -> log.error("상태를 업데이트할 이벤트를 찾을 수 없습니다. OrderId: {}", orderId)
        );
    }
}