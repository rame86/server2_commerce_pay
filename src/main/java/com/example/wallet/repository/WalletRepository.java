// src/main/java/com/example/payment/repository/WalletRepository.java
package com.example.wallet.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.wallet.domain.Wallet;

/**
 * [지갑 도메인 레포지토리]
 * UUID 기반의 기본 CRUD 기능을 제공하며, 
 * Core 서비스의 회원 식별자(memberId)를 이용한 커스텀 조회 로직을 포함함.
 */
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /**
     * [회원 식별자로 지갑 조회]
     * Core 서비스로부터 전달받은 memberId를 기반으로 해당 사용자의 지갑을 찾음.
     * * @param memberId Core 서비스의 회원 고유 ID
     * @return Optional 객체로 감싼 지갑 엔티티 (존재하지 않을 경우를 대비한 안전한 처리 지원)
     */
    Optional<Wallet> findByMemberId(Long memberId);
}