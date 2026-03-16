// src/main/java/com/example/payment/repository/LedgerRepository.java
package com.example.payment.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.payment.domain.Ledger;

@Repository
public interface LedgerRepository extends JpaRepository<Ledger, UUID> {
    // 멱등성 검증용: 주문번호와 결제/환불 타입으로 기존 정산 내역 존재 여부 확인
    boolean existsByOrderIdAndRevenueType(String orderId, String revenueType);

    // 기간 내 모든 정산 내역 단순 조회
    List<Ledger> findByCreatedAtBetween(OffsetDateTime startDate, OffsetDateTime endDate);

}