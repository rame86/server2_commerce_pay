// src/main/java/com/example/payment/repository/TransactionHistoryRepository.java

package com.example.payment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.payment.domain.TransactionHistory;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, UUID> {
    boolean existsByReferenceIdAndTransactionType(String referenceId, String transactionType);

    TransactionHistory findTopByReferenceIdAndTransactionType(String referenceId, String transactionType);
}