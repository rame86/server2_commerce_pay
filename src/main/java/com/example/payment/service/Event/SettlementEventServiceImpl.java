// src/main/java/com/example/payment/service/Event/SettlementEventServiceImpl.java
package com.example.payment.service.Event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.Ledger;
import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.dto.response.AdminDashboardResponseDTO;
import com.example.payment.dto.response.ArtistSettlementRowDTO;
import com.example.payment.dto.response.DashboardSummaryDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;
import com.example.payment.repository.LedgerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementEventServiceImpl implements SettlementEventService {

    private final PaymentEventProducer producer;
    private final LedgerRepository ledgerRepository;

    @Override
    @Transactional(readOnly = true)
    public void processAdminSettlement(PaymentEventDTO dto) {
        String replyKey = dto.getReplyRoutingKey();
        String orderId = dto.getOrderId();

        try {
            producer.sendDataResponse(replyKey, orderId, "PROCESSING", "관리자 대시보드 데이터 집계 중입니다.", dto.getType(), null);

            // 1. 조회 기간 설정 (당월 1일 ~ 말일)
            YearMonth currentMonth = YearMonth.now();
            OffsetDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay()
                    .atOffset(OffsetDateTime.now().getOffset());
            OffsetDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59)
                    .atOffset(OffsetDateTime.now().getOffset());

            // 2. 전체 데이터 DB 단건 일괄 조회
            List<Ledger> ledgers = ledgerRepository.findByCreatedAtBetween(startOfMonth, endOfMonth);

            // 3. 메모리 데이터 집계
            DashboardSummaryDTO summary = calculateDashboardSummary(ledgers);
            List<ArtistSettlementRowDTO> artistSettlements = calculateArtistSettlements(ledgers);

            // [수정] Record 타입에 맞게 accessor 호출 (get 제거)
            log.info("[ADMIN_SETTLEMENT] 집계 데이터 확인 - OrderId: {}, 조회된 원장 수: {}, 아티스트 정산 항목 수: {}",
                    orderId, ledgers.size(), artistSettlements.size());

            log.info("[ADMIN_SETTLEMENT] 요약 정보 - 총 매출: {}, 수수료: {}, 정산 예정: {}, 정산 완료: {}",
                    summary.totalGrossAmount(), // getTotalGrossAmount() -> totalGrossAmount()
                    summary.totalPlatformFee(), // getTotalPlatformFee() -> totalPlatformFee()
                    summary.totalExpectedAmount(), // getTotalExpectedAmount() -> totalExpectedAmount()
                    summary.totalSettledAmount()); // getTotalSettledAmount() -> totalSettledAmount()

            // 4. 응답 전송
            AdminDashboardResponseDTO payload = new AdminDashboardResponseDTO(summary, artistSettlements);
            producer.sendDataResponse(replyKey, orderId, "COMPLETE", "대시보드 데이터 조회 및 집계 성공", dto.getType(), payload);

            log.info("[ADMIN_SETTLEMENT] 대시보드 데이터 응답 발송 완료 - 라우팅키: {}", replyKey);

        } catch (Exception e) {
            log.error("[ADMIN_SETTLEMENT] 대시보드 데이터 집계 실패 - 사유: {}", e.getMessage(), e);
            producer.sendDataResponse(replyKey, orderId, "FAIL", "조회 실패: " + e.getMessage(), "ERROR", null);
        }
    }

    /**
     * 상단 요약 데이터 단일 반복문으로 성능 최적화 집계
     */
    private DashboardSummaryDTO calculateDashboardSummary(List<Ledger> ledgers) {
        BigDecimal totalGrossAmount = BigDecimal.ZERO;
        BigDecimal totalPlatformFee = BigDecimal.ZERO;
        BigDecimal totalExpectedAmount = BigDecimal.ZERO;
        BigDecimal totalSettledAmount = BigDecimal.ZERO;

        for (Ledger l : ledgers) {
            totalGrossAmount = totalGrossAmount.add(l.getGrossAmount() != null ? l.getGrossAmount() : BigDecimal.ZERO);
            totalPlatformFee = totalPlatformFee.add(l.getFeeAmount() != null ? l.getFeeAmount() : BigDecimal.ZERO);

            BigDecimal net = l.getNetAmount() != null ? l.getNetAmount() : BigDecimal.ZERO;

            // SettlementServiceImpl 로직 기준, 완료 처리는 'COMPLETED' 문자열 비교
            if ("PENDING".equalsIgnoreCase(l.getStatus())) {
                totalExpectedAmount = totalExpectedAmount.add(net);
            } else if ("COMPLETED".equalsIgnoreCase(l.getStatus())) {
                totalSettledAmount = totalSettledAmount.add(net);
            }
        }

        return new DashboardSummaryDTO(totalGrossAmount, totalPlatformFee, totalExpectedAmount, totalSettledAmount);
    }

    /**
     * 하단 아티스트별 정산 데이터 그룹핑 집계
     */
    private List<ArtistSettlementRowDTO> calculateArtistSettlements(List<Ledger> ledgers) {
        // 복합 키용 레코드
        record ArtistStatusKey(Long artistId, String status) {
        }

        Map<ArtistStatusKey, List<Ledger>> groupedLedgers = ledgers.stream()
                .collect(Collectors.groupingBy(l -> new ArtistStatusKey(l.getArtistId(), l.getStatus())));

        return groupedLedgers.entrySet().stream()
                .map(entry -> {
                    ArtistStatusKey key = entry.getKey();
                    List<Ledger> artistLedgers = entry.getValue();

                    BigDecimal sumGross = artistLedgers.stream()
                            .map(l -> l.getGrossAmount() != null ? l.getGrossAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal sumFee = artistLedgers.stream()
                            .map(l -> l.getFeeAmount() != null ? l.getFeeAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal sumNet = artistLedgers.stream()
                            .map(l -> l.getNetAmount() != null ? l.getNetAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    OffsetDateTime lastTxDate = artistLedgers.stream()
                            .map(Ledger::getCreatedAt)
                            .max(OffsetDateTime::compareTo)
                            .orElse(null);

                    return new ArtistSettlementRowDTO(
                            key.artistId(),
                            "아티스트 " + key.artistId(),
                            sumGross,
                            sumFee,
                            sumNet,
                            key.status(),
                            lastTxDate);
                })
                .toList();
    }
}