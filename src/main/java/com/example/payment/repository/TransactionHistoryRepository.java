// src/main/java/com/example/payment/repository/TransactionHistoryRepository.java

package com.example.payment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.payment.domain.TransactionHistory;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, UUID> {
    boolean existsByReferenceIdAndTransactionType(String referenceId, String transactionType);

    TransactionHistory findTopByReferenceIdAndTransactionType(String referenceId, String transactionType);

    // 지갑 ID 기반으로 최신순 거래 내역 조회
    List<TransactionHistory> findAllByWalletIdOrderByCreatedAtDesc(UUID walletId);

}