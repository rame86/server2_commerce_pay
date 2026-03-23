// src/main/java/com/example/settlement/service/ArtistSettlementServiceImpl.java
package com.example.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.ArtistAccount;
import com.example.payment.domain.Ledger;
import com.example.payment.repository.ArtistAccountRepository;
import com.example.settlement.dto.ArtistSettlementResponseDTO;
import com.example.settlement.dto.ArtistSettlementResponseDTO.MonthlyRevenueSummary;
import com.example.settlement.dto.ArtistSettlementResponseDTO.RevenueComposition;
import com.example.settlement.dto.ArtistSettlementResponseDTO.SettlementSummary;
import com.example.settlement.repository.ArtistLedgerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistSettlementServiceImpl implements ArtistSettlementService {

    private final ArtistLedgerRepository artistLedgerRepository;
    private final ArtistAccountRepository artistAccountRepository;

    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    @Transactional(readOnly = true)
    public ArtistSettlementResponseDTO getSettlementDashboard(Long artistId) {
        log.info("[SETTLEMENT] 아티스트 정산 대시보드 조회 - artistId: {}", artistId);

        // 1. 아티스트 계좌 정보 (누적 수익)
        ArtistAccount account = artistAccountRepository.findById(artistId)
                .orElseGet(() -> ArtistAccount.builder()
                        .artistId(artistId)
                        .totalBalance(BigDecimal.ZERO)
                        .withdrawableBalance(BigDecimal.ZERO)
                        .build());

        // 2. 전체 원장 조회 (최신순)
        List<Ledger> allLedgers = artistLedgerRepository.findAllByArtistIdOrderByCreatedAtDesc(artistId);

        // 3. 이번달 수익
        LocalDate now = LocalDate.now();
        BigDecimal thisMonthRevenue = artistLedgerRepository.sumThisMonthNetAmount(
                artistId, now.getYear(), now.getMonthValue());

        // 4. 정산 완료 / 정산 예정 분리
        //    - 이번달 이전 COMPLETED: 정산 완료 (이미 지급)
        //    - 이번달 포함 이후 COMPLETED: 정산 예정
        OffsetDateTime startOfThisMonth = now.withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        BigDecimal completedSettlement = allLedgers.stream()
                .filter(l -> "COMPLETED".equals(l.getStatus())
                        && l.getCreatedAt() != null
                        && l.getCreatedAt().isBefore(startOfThisMonth)
                        && l.getNetAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(Ledger::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingSettlement = allLedgers.stream()
                .filter(l -> "COMPLETED".equals(l.getStatus())
                        && l.getCreatedAt() != null
                        && !l.getCreatedAt().isBefore(startOfThisMonth)
                        && l.getNetAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(Ledger::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long completedCount = allLedgers.stream()
                .filter(l -> "COMPLETED".equals(l.getStatus())
                        && l.getCreatedAt() != null
                        && l.getCreatedAt().isBefore(startOfThisMonth))
                .map(l -> l.getCreatedAt().format(YEAR_MONTH_FMT))
                .distinct()
                .count();

        // 5. 월별 수익 트렌드 (최근 6개월, 유형별 분리)
        List<MonthlyRevenueSummary> monthlyTrend = buildMonthlyTrend(allLedgers, 6);

        // 6. 수익 구성 (도넛 차트)
        List<RevenueComposition> revenueComposition = buildRevenueComposition(allLedgers);

        // 7. 정산 내역 목록 (월별 그룹핑)
        List<SettlementSummary> settlements = buildSettlementSummaries(allLedgers);

        return ArtistSettlementResponseDTO.builder()
                .thisMonthRevenue(thisMonthRevenue)
                .totalAccumulatedRevenue(account.getTotalBalance())
                .pendingSettlement(pendingSettlement)
                .completedSettlement(completedSettlement)
                .completedCount((int) completedCount)
                .monthlyTrend(monthlyTrend)
                .revenueComposition(revenueComposition)
                .settlements(settlements)
                .build();
    }

    /**
     * 최근 N개월 월별 수익 트렌드 구성
     * revenueType: PAYMENT/DONATION/REFUND 기준으로 이벤트/굿즈/후원 분리
     */
    private List<MonthlyRevenueSummary> buildMonthlyTrend(List<Ledger> ledgers, int months) {
        LocalDate now = LocalDate.now();
        List<MonthlyRevenueSummary> trend = new ArrayList<>();

        for (int i = months - 1; i >= 0; i--) {
            LocalDate month = now.minusMonths(i);
            String yearMonth = month.format(YEAR_MONTH_FMT);

            List<Ledger> monthly = ledgers.stream()
                    .filter(l -> l.getCreatedAt() != null
                            && l.getCreatedAt().format(YEAR_MONTH_FMT).equals(yearMonth)
                            && l.getNetAmount().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());

            BigDecimal eventRevenue = monthly.stream()
                    .filter(l -> "PAYMENT".equals(l.getRevenueType()))
                    .map(Ledger::getNetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal goodsRevenue = monthly.stream()
                    .filter(l -> "GOODS".equals(l.getRevenueType()))
                    .map(Ledger::getNetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal donationRevenue = monthly.stream()
                    .filter(l -> "DONATION".equals(l.getRevenueType()))
                    .map(Ledger::getNetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            trend.add(MonthlyRevenueSummary.builder()
                    .yearMonth(yearMonth)
                    .eventRevenue(eventRevenue)
                    .goodsRevenue(goodsRevenue)
                    .donationRevenue(donationRevenue)
                    .build());
        }
        return trend;
    }

    /**
     * 수익 구성 (도넛 차트): 수익 유형별 비율 계산
     */
    private List<RevenueComposition> buildRevenueComposition(List<Ledger> ledgers) {
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        byType.put("이벤트 예매", BigDecimal.ZERO);
        byType.put("굿즈 판매", BigDecimal.ZERO);
        byType.put("팬 후원", BigDecimal.ZERO);

        for (Ledger l : ledgers) {
            if (l.getNetAmount().compareTo(BigDecimal.ZERO) <= 0) continue;
            String type = l.getRevenueType();
            String displayName = switch (type) {
                case "PAYMENT" -> "이벤트 예매";
                case "GOODS"   -> "굿즈 판매";
                case "DONATION"-> "팬 후원";
                default        -> null;
            };
            if (displayName != null) {
                byType.merge(displayName, l.getNetAmount(), BigDecimal::add);
            }
        }

        BigDecimal total = byType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return byType.entrySet().stream().map(entry -> {
            double pct = total.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                    : entry.getValue().divide(total, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();
            return RevenueComposition.builder()
                    .type(entry.getKey())
                    .amount(entry.getValue())
                    .percentage(Math.round(pct * 10.0) / 10.0)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 정산 내역 목록: 월별로 그룹핑하여 합산 후 목록 반환
     */
    private List<SettlementSummary> buildSettlementSummaries(List<Ledger> ledgers) {
        // 월(yearMonth) → [합산금액, 대표 날짜, 상태]
        Map<String, BigDecimal> amountByMonth = new LinkedHashMap<>();
        Map<String, String> dateByMonth = new LinkedHashMap<>();
        Map<String, String> statusByMonth = new LinkedHashMap<>();

        for (Ledger l : ledgers) {
            if (l.getCreatedAt() == null || l.getNetAmount().compareTo(BigDecimal.ZERO) <= 0) continue;
            String ym = l.getCreatedAt().format(YEAR_MONTH_FMT);
            amountByMonth.merge(ym, l.getNetAmount(), BigDecimal::add);
            // 각 월의 정산일: 해당 월의 마지막 기록 날짜를 사용 (창이 1개라면 해당 날짜)
            dateByMonth.putIfAbsent(ym, l.getCreatedAt().format(DATE_FMT));
            // 가장 최신 상태를 우선
            statusByMonth.putIfAbsent(ym, l.getStatus());
        }

        LocalDate now = LocalDate.now();

        return amountByMonth.entrySet().stream().map(entry -> {
            String ym = entry.getKey();
            int year  = Integer.parseInt(ym.substring(0, 4));
            int month = Integer.parseInt(ym.substring(5, 7));

            // 이번달 이후이면 PENDING, 아니면 DB 상태 사용
            String status = (year > now.getYear() || (year == now.getYear() && month >= now.getMonthValue()))
                    ? "PENDING" : statusByMonth.getOrDefault(ym, "COMPLETED");

            String period = year + "년 " + month + "월 정산";

            return SettlementSummary.builder()
                    .period(period)
                    .settlementDate(dateByMonth.getOrDefault(ym, ""))
                    .amount(entry.getValue())
                    .status(status)
                    .build();
        }).collect(Collectors.toList());
    }
}
