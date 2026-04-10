// src/main/java/com/example/payment/repository/LedgerRepository.java
package com.example.settlement.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.settlement.entity.Ledger;

/**
 * [정산 원장 레포지토리]
 * 정산 데이터의 영속성을 관리하며, 중복 정산 방지 및 기간별 내역 조회를 수행함.
 */
@Repository
public interface LedgerRepository extends JpaRepository<Ledger, UUID> {

    /**
     * [멱등성 검증: 중복 정산 방지]
     * 동일한 주문(OrderId)에 대해 특정 수익 유형(RevenueType)의 정산이 이미 처리되었는지 확인.
     * 네트워크 재시도 등으로 인한 이중 정산 사고를 원천 차단함.
     * * @param orderId     주문 식별자
     * @param revenueType 수익 유형 (TICKET, DONATION 등)
     * @return 존재 여부 (true: 이미 정산됨, false: 신규 정산 가능)
     */
    boolean existsByOrderIdAndRevenueType(String orderId, String revenueType);

    /**
     * [기간별 정산 내역 조회]
     * 특정 시작일과 종료일 사이에 생성된 모든 정산 데이터를 리스트로 반환.
     * 월간 정산 리포트 생성이나 대시보드 통계 산출 시 활용됨.
     * * @param startDate 조회 시작 일시
     * @param endDate   조회 종료 일시
     * @return 해당 기간 내의 정산 원장 리스트
     */
    List<Ledger> findByCreatedAtBetween(OffsetDateTime startDate, OffsetDateTime endDate);

}