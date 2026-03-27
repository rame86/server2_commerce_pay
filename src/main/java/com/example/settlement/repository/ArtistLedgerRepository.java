// src/main/java/com/example/settlement/repository/ArtistLedgerRepository.java
package com.example.settlement.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.payment.domain.Ledger;

/**
 * Settlement 도메인 전용 레포지토리.
 * payment.domain.Ledger 엔티티를 직접 참조하여 새 쿼리를 추가
 * (기존 payment 도메인의 LedgerRepository와 충돌 없이 별도로 운용)
 */
@Repository
public interface ArtistLedgerRepository extends JpaRepository<Ledger, UUID> {

    /** 특정 아티스트의 모든 정산 원장 최신순 조회 */
    List<Ledger> findAllByArtistIdOrderByCreatedAtDesc(Long artistId);

    /** 특정 아티스트 + 기간 내 정산 원장 조회 */
    List<Ledger> findAllByArtistIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long artistId, OffsetDateTime from, OffsetDateTime to);

    /** 특정 아티스트 + 수익 유형별 집계 (도넛 차트용) */
    @Query("""
            SELECT l.revenueType, SUM(l.netAmount)
            FROM Ledger l
            WHERE l.artistId = :artistId
              AND l.netAmount > 0
            GROUP BY l.revenueType
            """)
    List<Object[]> sumNetAmountByRevenueType(@Param("artistId") Long artistId);

    /** 
     * 특정 아티스트의 이번달 수익 합계 
     * 성능 및 DB 호환성을 위해 DB 내장 함수 대신 기간(Range) 조건 사용
     */
    @Query("""
            SELECT COALESCE(SUM(l.netAmount), 0)
            FROM Ledger l
            WHERE l.artistId = :artistId
              AND l.netAmount > 0
              AND l.createdAt >= :startDateTime
              AND l.createdAt < :endDateTime
            """)
    java.math.BigDecimal sumThisMonthNetAmount(
            @Param("artistId") Long artistId,
            @Param("startDateTime") OffsetDateTime startDateTime,
            @Param("endDateTime") OffsetDateTime endDateTime);


     //-----------------------------------------------------------------------------------------------------------
     // [아티스트 후원 내역 조회]
     /** * [추가] 아티스트 후원 내역 필터링 조회 (JPA Query Method)
     * - status: 'COMPLETED'
     * - revenueType: 'DONATION'
     */
    List<Ledger> findByArtistIdAndStatusAndRevenueTypeOrderByCreatedAtDesc(
            Long artistId, String status, String revenueType);
    //-----------------------------------------------------------------------------------------------------------
}