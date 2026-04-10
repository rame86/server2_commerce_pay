package com.example.settlement.repository;

import java.util.List;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.settlement.entity.MonthlyTrend;

@Repository
public interface MonthlyTrendRepository extends JpaRepository<MonthlyTrend, Long> {
    
    /**
     * 특정 월의 트렌드 데이터를 조회함 (YYYY-MM).
     */
    Optional<MonthlyTrend> findByMonth(String month);
    
    /**
     * 월별로 오름차순 정렬하여 모든 트렌드 데이터를 조회함.
     */
    List<MonthlyTrend> findAllByOrderByMonthAsc();
}
