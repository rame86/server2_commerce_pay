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
import com.example.settlement.dto.ArtistDonationResponse;
import com.example.settlement.dto.ArtistDonationResponse.DailyTrendDTO;
import com.example.settlement.dto.ArtistSettlementResponseDTO;
import com.example.settlement.dto.ArtistSettlementResponseDTO.MonthlyRevenueSummary;
import com.example.settlement.dto.ArtistSettlementResponseDTO.RevenueComposition;
import com.example.settlement.dto.ArtistSettlementResponseDTO.SettlementSummary;
import com.example.settlement.repository.ArtistLedgerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [아티스트 정산 대시보드 서비스 구현체]
 * 정산 원장(Ledger) 데이터를 분석하여 실시간 수익, 월별 트렌드, 수익 비중 등의 
 * 인사이트를 제공함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistSettlementServiceImpl implements ArtistSettlementService {

    private final ArtistLedgerRepository artistLedgerRepository;
    private final ArtistAccountRepository artistAccountRepository;

    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * [아티스트 정산 대시보드 통합 조회]
     * 정산 요약, 월별 추이, 수익 구성, 상세 내역을 한 번에 집계하여 반환함.
     */
    @Override
    @Transactional(readOnly = true)
    public ArtistSettlementResponseDTO getSettlementDashboard(Long artistId) {
        log.info(">>> [ARTIST_DASHBOARD] 대시보드 데이터 집계 시작 - ArtistId: {}", artistId);

        // 1. 아티스트 계좌 정보 조회 (누적 수익 확인용)
        // 계좌 정보가 없는 신규 아티스트의 경우 0원으로 초기화된 객체 사용
        ArtistAccount account = artistAccountRepository.findById(artistId)
                .orElseGet(() -> ArtistAccount.builder()
                        .artistId(artistId)
                        .totalBalance(BigDecimal.ZERO)
                        .withdrawableBalance(BigDecimal.ZERO)
                        .build());

        // 2. 해당 아티스트의 전체 정산 원장 로드 (최신순)
        List<Ledger> allLedgers = artistLedgerRepository.findAllByArtistIdOrderByCreatedAtDesc(artistId);

        // 3. 이번달 수익 계산 (현재 월의 1일 00:00:00부터 기준)
        LocalDate now = LocalDate.now();
        OffsetDateTime startOfThisMonth = now.withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime startOfNextMonth = startOfThisMonth.plusMonths(1);

        BigDecimal thisMonthRevenue = artistLedgerRepository.sumThisMonthNetAmount(
                artistId, startOfThisMonth, startOfNextMonth);

        // 4. 정산 완료 및 예정 금액 분리 집계
        // - 이번달 이전 완료건: 이미 지급된 '정산 완료'
        // - 이번달 포함 이후건: 아직 정산 사이클이 돌아가지 않은 '정산 예정'
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

        // 정산이 완료된 총 횟수 (월 단위 카운트)
        long completedCount = allLedgers.stream()
                .filter(l -> "COMPLETED".equals(l.getStatus())
                        && l.getCreatedAt() != null
                        && l.getCreatedAt().isBefore(startOfThisMonth))
                .map(l -> l.getCreatedAt().format(YEAR_MONTH_FMT))
                .distinct()
                .count();

        // 5. 월별 수익 트렌드 구성 (최근 6개월 데이터 추출)
        List<MonthlyRevenueSummary> monthlyTrend = buildMonthlyTrend(allLedgers, 6);

        // 6. 수익 비중 분석 (이벤트/굿즈/후원 비율 계산)
        List<RevenueComposition> revenueComposition = buildRevenueComposition(allLedgers);

        // 7. 상세 정산 내역 목록 (월별 그룹화 및 합산)
        List<SettlementSummary> settlements = buildSettlementSummaries(allLedgers);

        log.info(">>> [ARTIST_DASHBOARD] 집계 완료 - 이번달 수익: {}, 누적 수익: {}", 
                 thisMonthRevenue, account.getTotalBalance());

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
     * [최근 N개월 수익 추이 빌더]
     * 수익 유형별로 데이터를 분류하여 월별 성장 추이를 시각화하기 위한 데이터를 생성함.
     */
    private List<MonthlyRevenueSummary> buildMonthlyTrend(List<Ledger> ledgers, int months) {
        log.debug(">>> [DASHBOARD_UTIL] 월별 수익 트렌드 분석 중 (최근 {}개월)", months);
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

            // 각 카테고리별 수익 합계 계산
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
     * [수익 비중 빌더]
     * 도넛 차트용 데이터. 전체 수익 대비 각 항목의 비율(%)을 소수점 첫째 자리까지 계산함.
     */
    private List<RevenueComposition> buildRevenueComposition(List<Ledger> ledgers) {
        log.debug(">>> [DASHBOARD_UTIL] 수익 구성 비율 계산 중");
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
     * [정산 상세 내역 빌더]
     * 개별 원장들을 월별로 묶어 'N년 M월 정산' 단위로 합산한 리스트를 생성함.
     */
    private List<SettlementSummary> buildSettlementSummaries(List<Ledger> ledgers) {
        log.debug(">>> [DASHBOARD_UTIL] 월별 정산 내역 그룹화 중");
        Map<String, BigDecimal> amountByMonth = new LinkedHashMap<>();
        Map<String, String> dateByMonth = new LinkedHashMap<>();
        Map<String, String> statusByMonth = new LinkedHashMap<>();

        for (Ledger l : ledgers) {
            if (l.getCreatedAt() == null || l.getNetAmount().compareTo(BigDecimal.ZERO) <= 0) continue;
            String ym = l.getCreatedAt().format(YEAR_MONTH_FMT);
            amountByMonth.merge(ym, l.getNetAmount(), BigDecimal::add);
            
            // 월별 대표 정산 날짜 및 상태 캡처
            dateByMonth.putIfAbsent(ym, l.getCreatedAt().format(DATE_FMT));
            statusByMonth.putIfAbsent(ym, l.getStatus());
        }

        LocalDate now = LocalDate.now();

        return amountByMonth.entrySet().stream().map(entry -> {
            String ym = entry.getKey();
            int year  = Integer.parseInt(ym.substring(0, 4));
            int month = Integer.parseInt(ym.substring(5, 7));

            // 비즈니스 룰: 현재 월과 미래 월은 무조건 PENDING 처리
            String status = (year > now.getYear() || (year == now.getYear() && month >= now.getMonthValue()))
                    ? "PENDING" : statusByMonth.getOrDefault(ym, "COMPLETED");

            return SettlementSummary.builder()
                    .period(year + "년 " + month + "월 정산")
                    .settlementDate(dateByMonth.getOrDefault(ym, ""))
                    .amount(entry.getValue())
                    .status(status)
                    .build();
        }).collect(Collectors.toList());
    }


    //-----------------------------------------------------------------------------------------------------------
    //-----------------------------------------------------------------------------------------------------------
    /**
     * [수민 수정] 아티스트 후원 내역 및 통계 조회
     * - status: COMPLETED, revenueType: DONATION 필터링
     */
    @Override
    @Transactional(readOnly = true)
    public ArtistDonationResponse artistDonation(Long artistId) {
        log.info(">>> [DONATION] 아티스트 후원 내역 조회 - artistId: {}", artistId);

        // 1. 후원 데이터 전체 조회 (최신순)
        // 레포지토리에 findByArtistIdAndStatusAndRevenueTypeOrderByCreatedAtDesc 필요
        List<Ledger> donationLedgers = artistLedgerRepository
                .findByArtistIdAndStatusAndRevenueTypeOrderByCreatedAtDesc(artistId, "COMPLETED", "DONATION");

        // 2. 시간 범위 설정 (이번 달)
        LocalDate now = LocalDate.now();
        OffsetDateTime startOfThisMonth = now.withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        // 3. 통계 계산
        // 총 누적 후원금
        BigDecimal totalAmount = donationLedgers.stream()
                .map(Ledger::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 이번 달 후원금
        BigDecimal thisMonthAmount = donationLedgers.stream()
                .filter(l -> l.getCreatedAt() != null && !l.getCreatedAt().isBefore(startOfThisMonth))
                .map(Ledger::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 최고 후원금 (단일건)
        BigDecimal maxSingleDonation = donationLedgers.stream()
                .map(Ledger::getGrossAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // 4. 최근 후원 추이 (최근 7일 데이터 그룹핑)
        Map<String, BigDecimal> dailyMap = donationLedgers.stream()
                .limit(30) // 최근 30건 내에서 처리
                .collect(Collectors.groupingBy(
                        l -> l.getCreatedAt().format(DateTimeFormatter.ofPattern("MM/dd")),
                        LinkedHashMap::new,
                        Collectors.mapping(Ledger::getGrossAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));

        List<DailyTrendDTO> dailyTrend = dailyMap.entrySet().stream()
                .map(entry -> new DailyTrendDTO(entry.getKey(), entry.getValue().longValue()))
                .collect(Collectors.toList());

        // 5. Top 서포터즈 (이름별 그룹핑 및 합산 후 정렬)
        Map<String, BigDecimal> donorMap = donationLedgers.stream()
                .collect(Collectors.toMap(
                        l -> (String)(l.getEventTitle() != null ? l.getEventTitle() : "익명의 팬"), 
                        Ledger::getGrossAmount,
                        BigDecimal::add
                ));

        // 6. 응원 메시지 목록
        List<ArtistDonationResponse.DonationMessageDTO> messages = donationLedgers.stream()
                .map(l -> new ArtistDonationResponse.DonationMessageDTO(
                        l.getEventTitle() != null ? l.getEventTitle() : "익명의 팬", 
                        l.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        l.getGrossAmount().longValue(),
                        l.getEventTitle() 
                ))
                .limit(20)
                .collect(Collectors.toList());

        // 7. 리스트 변환 및 정렬
        List<ArtistDonationResponse.DonorDTO> topDonors = donorMap.entrySet().stream()
                .map(entry -> new ArtistDonationResponse.DonorDTO(
                        entry.getKey(), 
                        entry.getValue().longValue()
                ))
                .sorted((d1, d2) -> Long.compare(d2.getTotal(), d1.getTotal())) 
                .limit(5)
                .collect(Collectors.toList());

        return ArtistDonationResponse.builder()
                .thisMonthAmount(thisMonthAmount.longValue())
                .totalAmount(totalAmount.longValue())
                .donorCount(donorMap.size())
                .maxSingleDonation(maxSingleDonation.longValue())
                .dailyTrend(dailyTrend)
                .topDonors(topDonors)
                .messages(messages)
                .build();
    }
    //-----------------------------------------------------------------------------------------------------------
    


}