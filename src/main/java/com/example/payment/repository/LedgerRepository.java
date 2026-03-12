// src/main/java/com/example/payment/repository/LedgerRepository.java
package com.example.payment.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.payment.domain.Ledger;

@Repository
public interface LedgerRepository extends JpaRepository<Ledger, UUID> {
    // 멱등성 검증용: 주문번호로 기존 정산 내역 존재 여부 확인
    boolean existsByOrderId(String orderId);
}