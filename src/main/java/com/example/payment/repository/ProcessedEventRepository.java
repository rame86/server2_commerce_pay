// src/main/java/com/example/payment/repository/ProcessedEventRepository.java
package com.example.payment.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.payment.entity.TransctionEvent;

public interface ProcessedEventRepository extends JpaRepository<TransctionEvent, UUID> {
    Optional<TransctionEvent> findByOrderId(String orderId);
}