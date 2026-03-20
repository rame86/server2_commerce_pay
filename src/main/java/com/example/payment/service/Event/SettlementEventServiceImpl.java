// src/main/java/com/example/payment/service/Event/SettlementEventServiceImpl.java
package com.example.payment.service.Event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.ArtistAccount;
import com.example.payment.domain.Ledger;
import com.example.payment.domain.TransactionHistory;
import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.dto.response.AdminDashboardResponseDTO;
import com.example.payment.dto.response.ArtistSettlementRowDTO;
import com.example.payment.dto.response.DashboardSummaryDTO;
import com.example.payment.dto.response.PointHistoryDTO;
import com.example.payment.dto.response.PurchaseHistoryDTO;
import com.example.payment.dto.response.UserDetailPaymentResponseDTO;
import com.example.payment.dto.response.UserPaymentSummaryDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;
import com.example.payment.repository.LedgerRepository;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.payment.service.settlement.SettlementService;
import com.example.wallet.domain.Wallet;
import com.example.wallet.dto.WalletDTO;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementEventServiceImpl implements SettlementEventService {

    private final WalletService walletService;
    private final SettlementService settlementService;
    private final PaymentEventProducer producer;
    
    // 어드민 통계 및 조회를 위한 Repository 모음
    private final LedgerRepository ledgerRepository;
    private final WalletRepository walletRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;

    @Override
    @Transactional(readOnly = true)
    public void processAdminSettlement(PaymentEventDTO dto) {
        String replyKey = dto.getReplyRoutingKey();
        String orderId = dto.getOrderId();

        try {
            producer.sendDataResponse(replyKey, orderId, "PROCESSING", "관리자 대시보드 데이터 집계 중입니다.", dto.getType(), null);

            // 1. 조회 기간 설정 (당월 1일 ~ 말일)
            YearMonth currentMonth = YearMonth.now();
            OffsetDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
            OffsetDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59).atOffset(OffsetDateTime.now().getOffset());

            // 2. 전체 데이터 DB 단건 일괄 조회
            List<Ledger> ledgers = ledgerRepository.findByCreatedAtBetween(startOfMonth, endOfMonth);

            // 3. 메모리 데이터 집계
            DashboardSummaryDTO summary = calculateDashboardSummary(ledgers);
            List<ArtistSettlementRowDTO> artistSettlements = calculateArtistSettlements(ledgers);

            log.info("[ADMIN_SETTLEMENT] 집계 데이터 확인 - OrderId: {}, 조회된 원장 수: {}", orderId, ledgers.size());

            // 4. 응답 전송
            AdminDashboardResponseDTO payload = new AdminDashboardResponseDTO(summary, artistSettlements);
            producer.sendDataResponse(replyKey, orderId, "COMPLETE", "대시보드 데이터 조회 및 집계 성공", dto.getType(), payload);

        } catch (Exception e) {
            log.error("[ADMIN_SETTLEMENT] 대시보드 데이터 집계 실패 - 사유: {}", e.getMessage(), e);
            producer.sendDataResponse(replyKey, orderId, "FAIL", "조회 실패: " + e.getMessage(), "ERROR", null);
        }
    }

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

    // 1. 관리자용1: 모든 지갑 정보 조회 (GETALL)
    @Override
    public void processAdminGetAll(PaymentEventDTO dto) {
        try {
            log.info(">>> [ADMIN-GETALL] 모든 지갑 정보 조회 요청 수신");
            List<WalletDTO> payload = walletService.getAllWallets();
            sendSuccessResponse(dto, payload);
        } catch (Exception e) {
            sendFailResponse(dto, "모든 지갑 정보 조회 실패", e);
        }
    }

    // 2. 관리자용2: 아티스트 정산 정보 조회 (ARTIST)
    @Override
    public void processAdminArtistDetail(PaymentEventDTO dto) {
        try {
            Long artistId = dto.getArtistId();
            if (artistId == null) throw new IllegalArgumentException("artistId가 누락되었습니다.");

            log.info(">>> [ADMIN-ARTIST] 아티스트 {}번 정산 정보 조회", artistId);
            ArtistAccount payload = settlementService.getArtistAccount(artistId); // 아티스트 도메인 기능 재사용
            sendSuccessResponse(dto, payload);
        } catch (Exception e) {
            sendFailResponse(dto, "아티스트 정산 정보 조회 실패", e);
        }
    }

    // 3. 관리자용3: 유저 결제 요약 정보 조회 (SUMMARY)
    @Override
    @Transactional(readOnly = true)
    public void processAdminSummary(PaymentEventDTO dto) {
        try {
            List<Long> memberIds = dto.getAllMemberId();
            if (memberIds == null || memberIds.isEmpty()) {
                throw new IllegalArgumentException("allMemberId 리스트가 누락되었거나 비어있습니다.");
            }

            log.info(">>> [ADMIN-SUMMARY] 유저 결제 요약 정보 조회: {}명", memberIds.size());
            
            List<UserPaymentSummaryDTO> payload = memberIds.stream().map(mid -> {
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

            sendSuccessResponse(dto, payload);
        } catch (Exception e) {
            sendFailResponse(dto, "유저 결제 요약 정보 조회 실패", e);
        }
    }

    // 4. 관리자용4: 유저 상세 내역 조회 (USER_DETAIL)
    @Override
    @Transactional(readOnly = true)
    public void processAdminUserDetail(PaymentEventDTO dto) {
        try {
            Long memberId = dto.getMemberId();
            if (memberId == null) throw new IllegalArgumentException("memberId가 누락되었습니다.");

            log.info(">>> [ADMIN-USER_DETAIL] 유저 ID {} 상세 내역 조회", memberId);
            
            Wallet wallet = walletRepository.findByMemberId(memberId).orElse(null);
            UserDetailPaymentResponseDTO payload;

            if (wallet == null) {
                payload = UserDetailPaymentResponseDTO.builder()
                        .totalPurchases(0)
                        .pointBalance(0L)
                        .purchaseHistory(Collections.emptyList())
                        .pointHistory(Collections.emptyList())
                        .build();
            } else {
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

                payload = UserDetailPaymentResponseDTO.builder()
                    .totalPurchases(purchaseHistory.size())
                    .pointBalance(wallet.getBalance().longValue())
                    .purchaseHistory(purchaseHistory)
                    .pointHistory(pointHistory)
                    .build();
            }

            sendSuccessResponse(dto, payload);
        } catch (Exception e) {
            sendFailResponse(dto, "유저 상세 내역 조회 실패", e);
        }
    }
    // 성공
    private <T> void sendSuccessResponse(PaymentEventDTO dto, T payload) {
        producer.sendDataResponse(
                dto.getReplyRoutingKey(),
                dto.getOrderId(), 
                "SUCCESS",
                "조회 완료",
                "ADMIN",
                payload);
        log.info("[ADMIN-{}] 응답 발송 완료 - 목적지: {}", dto.getOrderId(), dto.getReplyRoutingKey());
    }
    // 실패
    private void sendFailResponse(PaymentEventDTO dto, String errorMessage, Exception e) {
        log.error("[ADMIN-{}] 처리 에러: {}", dto.getOrderId(), e.getMessage());
        producer.sendDataResponse(
                dto.getReplyRoutingKey(),
                dto.getOrderId(),
                "FAIL",
                errorMessage + ": " + e.getMessage(),
                "ADMIN",
                null);
    }
}