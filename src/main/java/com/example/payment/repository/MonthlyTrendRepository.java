package com.example.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.payment.domain.MonthlyTrend;

@Repository
public interface MonthlyTrendRepository extends JpaRepository<MonthlyTrend, String> {
    
    /**
     * 월별로 오름차순 정렬하여 모든 트렌드 데이터를 조회함.
     */
    List<MonthlyTrend> findAllByOrderByMonthAsc();
}
