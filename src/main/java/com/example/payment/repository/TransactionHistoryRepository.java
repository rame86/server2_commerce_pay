// src/main/java/com/example/payment/repository/TransactionHistoryRepository.java
package com.example.payment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.payment.domain.TransactionHistory;

/**
 * [거래 이력 레포지토리]
 * 결제, 충전, 환불 등 지갑의 모든 트랜잭션 기록에 대한 데이터 접근을 담당함.
 */
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, UUID> {

    /**
     * [멱등성 검증] 
     * 특정 참조 ID(주문번호 등)와 거래 유형에 대해 이미 기록된 이력이 있는지 확인.
     * 동일한 주문에 대한 중복 처리(이중 결제/이중 환불)를 방지하는 첫 번째 방어선임.
     */
    boolean existsByReferenceIdAndTransactionType(String referenceId, String transactionType);

    /**
     * [참조 ID 기반 최신 내역 조회]
     * 특정 주문번호나 외부 ID에 해당하는 가장 최근의 거래 기록을 가져옴.
     * 환불 처리 시 원본 결제 금액이나 상태를 대조하기 위해 주로 사용됨.
     */
    TransactionHistory findTopByReferenceIdAndTransactionType(String referenceId, String transactionType);

    /**
     * [사용자별 내역 조회]
     * 특정 지갑의 모든 거래 내역을 생성 일시(createdAt) 기준 내림차순으로 정렬하여 반환함.
     * 마이페이지의 '최신 거래 내역' 목록 구성 시 사용되며, 인덱스 활용이 매우 중요한 쿼리임.
     */
    List<TransactionHistory> findAllByWalletIdOrderByCreatedAtDesc(UUID walletId);

    /**
     * [거래 횟수 통계]
     * 특정 지갑에서 특정 유형(예: PAYMENT)의 거래가 총 몇 건 발생했는지 집계함.
     * 사용자 등급 산정이나 '총 구매 횟수'와 같은 대시보드 지표 산출 시 활용됨.
     */
    int countByWalletIdAndTransactionType(UUID walletId, String type);
}