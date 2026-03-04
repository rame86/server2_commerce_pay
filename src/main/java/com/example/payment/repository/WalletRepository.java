// src/main/java/com/example/payment/repository/WalletRepository.java

package com.example.payment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.payment.domain.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByMemberId(Long memberId);
}