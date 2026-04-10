// src/main/java/com/example/payment/repository/ChargeRepository.java
package com.example.payment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.payment.entity.Charge;

/**
 * [결제 충전 레포지토리]
 * 충전 요청 원장(Charge) 엔티티에 대한 CRUD 및 데이터 영속성을 관리함.
 */
public interface ChargeRepository extends JpaRepository<Charge, UUID> {
    /**
     * [기능 정의]
     * 1. JpaRepository 상속: 별도 구현 없이 save(), findById(), delete() 등 표준 메서드 사용 가능.
     * 2. UUID 기반 식별: 충전 고유 ID인 UUID를 PK로 사용하여 분산 시스템 간 식별성을 보장함.
     */
}