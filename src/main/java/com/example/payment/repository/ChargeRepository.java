// src/main/java/com/example/payment/repository/ChargeRepository.java

package com.example.payment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.payment.domain.Charge;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {
}