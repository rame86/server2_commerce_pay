// src/main/java/com/example/payment/entity/ProcessedEvent.java
package com.example.payment.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [이벤트 처리 상태 엔티티]
 * Saga 패턴에서 각 이벤트의 진행 상태(PENDING, COMPLETE, FAIL)를 추적함.
 */
@Entity
@Table(name = "processed_events", schema = "pay")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TransctionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false, length = 255)
    private String orderId;

    @Column(name = "reply_routing_key", length = 255)
    private String replyRoutingKey;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, COMPLETE, FAIL

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    // 상태 변경 메서드
    public void updateStatus(String status) {
        this.status = status;
    }
}