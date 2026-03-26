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
import com.example.settlement.service.SettlementService;
import com.example.wallet.domain.Wallet;
import com.example.wallet.dto.WalletDTO;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [정산 및 관리자 이벤트 처리 서비스 구현체]
 * 메시지 큐를 통해 관리자(Admin) 측에서 요청한 대시보드 통계 및
 * 유저/아티스트 상세 조회 이벤트를 처리하고 응답을 비동기로 반환함.
 */
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

    /**
     * [관리자용 대시보드: 정산 통계 집계]
     * 당월 1일부터 말일까지의 정산 원장(Ledger)을 조회하여
     * 전체 통계와 아티스트별 정산 요약을 계산해 반환함.
     */
    @Override
    @Transactional(readOnly = true)
    public void processAdminSettlement(PaymentEventDTO dto) {
        String replyKey = dto.getReplyRoutingKey();
        String orderId = dto.getOrderId();

        log.info(">>> [ADMIN_SETTLEMENT] 정산 대시보드 통계 집계 시작 - OrderId: {}", orderId);

        try {
            // 시간이 걸릴 수 있으므로 'PROCESSING' 상태를 먼저 알려줌
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

            log.info(">>> [ADMIN_SETTLEMENT] 집계 완료 - OrderId: {}, 원장 수: {}건", orderId, ledgers.size());

            // 4. 응답 전송
            AdminDashboardResponseDTO payload = new AdminDashboardResponseDTO(summary, artistSettlements);
            producer.sendDataResponse(replyKey, orderId, "COMPLETE", "대시보드 데이터 조회 및 집계 성공", dto.getType(), payload);

        } catch (Exception e) {
            log.error(">>> [ADMIN_SETTLEMENT] 통계 집계 실패 - OrderId: {}, 사유: {}", orderId, e.getMessage());
            producer.sendDataResponse(replyKey, orderId, "FAIL", "조회 실패: " + e.getMessage(), "ERROR", null);
        }
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
    public void processAdminGetAll(PaymentEventDTO dto) {
        log.info(">>> [ADMIN_GETALL] 전체 지갑 정보 목록 조회 요청 수신 - OrderId: {}", dto.getOrderId());
        try {
            List<WalletDTO> payload = walletService.getAllWallets();
            sendSuccessResponse(dto, payload);
        } catch (Exception e) {
            sendFailResponse(dto, "모든 지갑 정보 조회 실패", e);
        }
    }

    /**
     * [관리자용: 특정 아티스트 상세 정산 계좌 조회]
     */
    @Override
    public void processAdminArtistDetail(PaymentEventDTO dto) {
        Long artistId = dto.getArtistId();
        log.info(">>> [ADMIN_ARTIST_DETAIL] 아티스트 정산 상세 조회 시작 - ArtistId: {}", artistId);
        
        try {
            if (artistId == null) throw new IllegalArgumentException("artistId가 누락되었습니다.");

            ArtistAccount payload = settlementService.getArtistAccount(artistId); // 아티스트 도메인 기능 재사용
            sendSuccessResponse(dto, payload);
        } catch (Exception e) {
            sendFailResponse(dto, "아티스트 정산 정보 조회 실패", e);
        }
    }

    /**
     * [관리자용: 여러 유저의 결제 요약 상태 조회]
     * 전달받은 다수의 memberId 리스트를 기반으로 각 유저의 잔액과 누적 결제 횟수를 집계함.
     */
    @Override
    @Transactional(readOnly = true)
    public void processAdminSummary(PaymentEventDTO dto) {
        List<Long> memberIds = dto.getAllMemberId();

        log.info(">>> [ADMIN_USER_SUMMARY] 유저 요약 정보 일괄 조회 시작 - 요청 인원수: {}", 
                 (memberIds != null ? memberIds.size() : 0));

        try {
            if (memberIds == null || memberIds.isEmpty()) {
                throw new IllegalArgumentException("allMemberId 리스트가 누락되었거나 비어있습니다.");
            }

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

    /**
     * [관리자용: 특정 유저의 상세 결제 및 포인트 내역 조회]
     */
    @Override
    @Transactional(readOnly = true)
    public void processAdminUserDetail(PaymentEventDTO dto) {
        Long memberId = dto.getMemberId();
        
        log.info(">>> [ADMIN_USER_DETAIL] 특정 유저 상세 내역 조회 시작 - MemberId: {}", memberId);

        try {
            if (memberId == null) throw new IllegalArgumentException("memberId가 누락되었습니다.");

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

    /**
     * [공통] 성공 응답 MQ 발송 로직
     */
    private <T> void sendSuccessResponse(PaymentEventDTO dto, T payload) {
        log.info(">>> [MQ_SUCCESS_SEND] 처리 완료. 응답 발송 준비 - OrderId: {}, Destination: {}", 
                 dto.getOrderId(), dto.getReplyRoutingKey());
                 
        producer.sendDataResponse(
                dto.getReplyRoutingKey(),
                dto.getOrderId(), 
                "SUCCESS",
                "조회 완료",
                "ADMIN",
                payload);
    }

    /**
     * [공통] 실패 응답 MQ 발송 로직
     */
    private void sendFailResponse(PaymentEventDTO dto, String errorMessage, Exception e) {
        log.error(">>> [MQ_FAIL_SEND] 처리 실패. 에러 응답 발송 - OrderId: {}, 사유: {}", 
                  dto.getOrderId(), e.getMessage());
                  
        producer.sendDataResponse(
                dto.getReplyRoutingKey(),
                dto.getOrderId(),
                "FAIL",
                errorMessage + ": " + e.getMessage(),
                "ADMIN",
                null);
    }
}