// src/main/java/com/example/admin/service/AdminSettlementEventServiceImpl.java
package com.example.admin.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.admin.dto.admin.response.AdminDashboardResponseDTO;
import com.example.admin.dto.admin.response.ArtistSettlementRowDTO;
import com.example.admin.dto.admin.response.DashboardSummaryDTO;
import com.example.admin.dto.admin.response.MonthlyTrendDTO;
import com.example.admin.dto.admin.response.PointHistoryDTO;
import com.example.admin.dto.admin.response.PurchaseHistoryDTO;
import com.example.admin.dto.admin.response.UserDetailPaymentResponseDTO;
import com.example.admin.dto.admin.response.UserPaymentSummaryDTO;
import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.payment.entity.TransactionHistory;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.settlement.entity.ArtistAccount;
import com.example.settlement.entity.Ledger;
import com.example.settlement.repository.LedgerRepository;
import com.example.settlement.repository.MonthlyTrendRepository;
import com.example.settlement.service.SettlementLedgerService;
import com.example.wallet.dto.WalletDTO;
import com.example.wallet.entity.Wallet;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [정산 및 관리자 이벤트 처리 서비스 구현체]
 * 순수 비즈니스 로직만 담당. 조회 및 집계된 데이터를 반환하며, 
 * 실제 MQ 메시지 발송 및 이벤트 상태 제어는 PaymentEventServiceImpl로 위임함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSettlementEventServiceImpl implements AdminSettlementEventService {

    private final WalletService walletService;
    private final SettlementLedgerService settlementService;
    
    // 어드민 통계 및 조회를 위한 Repository 모음
    private final LedgerRepository ledgerRepository;
    private final WalletRepository walletRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final MonthlyTrendRepository monthlyTrendRepository;
    
    /**
     * [관리자용 대시보드: 정산 통계 집계]
     */
    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponseDTO processAdminSettlement(PaymentEventRequestDTO dto) {
        log.info(">>> [ADMIN_SETTLEMENT] 정산 대시보드 통계 집계 로직 실행 - OrderId: {}", dto.getOrderId());

        YearMonth currentMonth = YearMonth.now();
        OffsetDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59).atOffset(OffsetDateTime.now().getOffset());

        List<Ledger> ledgers = ledgerRepository.findByCreatedAtBetween(startOfMonth, endOfMonth);

        DashboardSummaryDTO summary = calculateDashboardSummary(ledgers);
        List<ArtistSettlementRowDTO> artistSettlements = calculateArtistSettlements(ledgers);

        List<MonthlyTrendDTO> monthlyTrend = monthlyTrendRepository.findAllByOrderByMonthAsc().stream()
            .map(t -> new MonthlyTrendDTO(t.getMonth(), t.getTotalGross(), t.getTotalFee()))
            .toList();

        return new AdminDashboardResponseDTO(summary, artistSettlements, monthlyTrend);
    }

    /**
     * [내부 로직: 대시보드 상단 요약 통계 계산]
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

            if ("PENDING".equalsIgnoreCase(l.getStatus())) {
                totalExpectedAmount = totalExpectedAmount.add(net);
            } else if ("COMPLETED".equalsIgnoreCase(l.getStatus())) {
                totalSettledAmount = totalSettledAmount.add(net);
            }
        }
        return new DashboardSummaryDTO(totalGrossAmount, totalPlatformFee, totalExpectedAmount, totalSettledAmount);
    }

    /**
     * [내부 로직: 아티스트별 정산 목록 그룹화 계산]
     */
    private List<ArtistSettlementRowDTO> calculateArtistSettlements(List<Ledger> ledgers) {
        record ArtistStatusKey(Long artistId, String status) {}

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
                            sumGross, sumFee, sumNet, key.status(), lastTxDate);
                }).toList();
    }

    /**
     * [관리자용: 모든 지갑 정보 목록 조회]
     */
    @Override
    @Transactional(readOnly = true)
    public List<WalletDTO> processAdminGetAll(PaymentEventRequestDTO dto) {
        log.info(">>> [ADMIN_GETALL] 전체 지갑 정보 목록 조회 로직 실행 - OrderId: {}", dto.getOrderId());
        return walletService.getAllWallets();
    }

    /**
     * [관리자용: 특정 아티스트 상세 정산 계좌 조회]
     */
    @Override
    @Transactional(readOnly = true)
    public ArtistAccount processAdminArtistDetail(PaymentEventRequestDTO dto) {
        log.info(">>> [ADMIN_ARTIST_DETAIL] 아티스트 정산 상세 조회 로직 실행 - ArtistId: {}", dto.getArtistId());
        if (dto.getArtistId() == null) throw new IllegalArgumentException("artistId가 누락되었습니다.");
        
        return settlementService.getArtistAccount(dto.getArtistId());
    }

    /**
     * [관리자용: 여러 유저의 결제 요약 상태 조회]
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserPaymentSummaryDTO> processAdminSummary(PaymentEventRequestDTO dto) {
        List<Long> memberIds = dto.getAllMemberId();
        log.info(">>> [ADMIN_USER_SUMMARY] 유저 요약 정보 일괄 조회 로직 실행 - 요청 인원수: {}", (memberIds != null ? memberIds.size() : 0));

        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException("allMemberId 리스트가 누락되었거나 비어있습니다.");
        }

        return memberIds.stream().map(mid -> {
            return walletRepository.findByMemberId(mid)
                .map(wallet -> {
                    int count = transactionHistoryRepository.countByWalletIdAndTransactionType(wallet.getWalletId(), "PAYMENT");
                    return UserPaymentSummaryDTO.builder()
                        .memberId(mid)
                        .purchaseCount(count)
                        .balance(wallet.getBalance().longValue())
                        .version(wallet.getVersion())
                        .build();
                }).orElse(new UserPaymentSummaryDTO(mid, 0, 0L, 0));
        }).collect(Collectors.toList());
    }

    /**
     * [관리자용: 특정 유저의 상세 결제 및 포인트 내역 조회]
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetailPaymentResponseDTO processAdminUserDetail(PaymentEventRequestDTO dto) {
        Long memberId = dto.getMemberId();
        log.info(">>> [ADMIN_USER_DETAIL] 특정 유저 상세 내역 조회 로직 실행 - MemberId: {}", memberId);

        if (memberId == null) throw new IllegalArgumentException("memberId가 누락되었습니다.");

        Wallet wallet = walletRepository.findByMemberId(memberId).orElse(null);

        if (wallet == null) {
            return UserDetailPaymentResponseDTO.builder()
                    .totalPurchases(0)
                    .pointBalance(0L)
                    .purchaseHistory(Collections.emptyList())
                    .pointHistory(Collections.emptyList())
                    .build();
        } 

        List<TransactionHistory> allHistories = transactionHistoryRepository
            .findAllByWalletIdOrderByCreatedAtDesc(wallet.getWalletId());
        
        List<PurchaseHistoryDTO> purchaseHistory = allHistories.stream()
            .filter(h -> "PAYMENT".equals(h.getTransactionType()))
            .map(h -> PurchaseHistoryDTO.builder()
                .purchasedAt(h.getCreatedAt().toString())
                .itemName(h.getDescription())
                .amount(h.getAmount().longValue())
                .quantity(h.getQuantity())
                .status(h.getTransactionType())
                .build()).toList();
        
        List<PointHistoryDTO> pointHistory = allHistories.stream()
            .map(h -> PointHistoryDTO.builder()
                .processedAt(h.getCreatedAt().toString())
                .type(h.getTransactionType())
                .amount(h.getAmount().longValue())
                .description(h.getDescription())
                .balanceAfter(h.getBalanceAfter().longValue())
                .build()).toList();

        return UserDetailPaymentResponseDTO.builder()
            .totalPurchases(purchaseHistory.size())
            .pointBalance(wallet.getBalance().longValue())
            .purchaseHistory(purchaseHistory)
            .pointHistory(pointHistory)
            .build();
    }
}