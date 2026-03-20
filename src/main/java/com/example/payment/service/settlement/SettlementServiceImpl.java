// src/main/java/com/example/payment/service/SettlementServiceImpl.java
package com.example.payment.service.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.ArtistAccount;
import com.example.payment.domain.Ledger;
import com.example.payment.domain.TransactionHistory;
import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.dto.response.PointHistoryDTO;
import com.example.payment.dto.response.PurchaseHistoryDTO;
import com.example.payment.dto.response.UserDetailPaymentResponseDTO;
import com.example.payment.dto.response.UserPaymentSummaryDTO;
import com.example.payment.repository.ArtistAccountRepository;
import com.example.payment.repository.LedgerRepository;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.wallet.domain.Wallet;
import com.example.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final ArtistAccountRepository artistAccountRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;

    @Override
    @Transactional
    public void processSettlement(PaymentEventDTO dto) {
        // 1. 아티스트 ID가 없는 결제건은 정산 제외 (일반 상품 등)
        if (dto.getArtistId() == null) {
            return;
        }

        // 2. 멱등성 보장: 동일한 주문 번호(orderId)와 거래 타입(PAYMENT/REFUND)으로 이미 처리되었는지 확인
        if (ledgerRepository.existsByOrderIdAndRevenueType(dto.getOrderId(), dto.getType())) {
            log.info("이미 처리된 주문입니다. 주문번호: {}, 타입: {}", dto.getOrderId(), dto.getType());
            return;
        }

        // 3. 아티스트 계좌 조회 (없으면 신규 생성 - 시스템 구조에 따라 생략 가능) [cite: 180]
        ArtistAccount account = artistAccountRepository.findById(dto.getArtistId())
                .orElseGet(() -> {
                    ArtistAccount newAccount = ArtistAccount.builder()
                            .artistId(dto.getArtistId())
                            .build(); // 생성자에서 나머지 필드는 ZERO로 초기화됨
                    return artistAccountRepository.save(newAccount);
                });

        // 4. 정산 금액 계산 (절댓값으로 기준을 잡은 후 부호 결정)
        // 상품 원가 (gross_amount)는 originalPrice 사용
        BigDecimal baseAmount = dto.getOriginalPrice().multiply(BigDecimal.valueOf(dto.getQuantity()));
        BigDecimal grossAmount = baseAmount.abs();

        // 플랫폼 수수료 (fee_amount) 계산
        BigDecimal feeRate = dto.getFee() != null
                ? dto.getFee().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal feeAmount = grossAmount.multiply(feeRate);
        // 아티스트 실제 정산액 (net_amount) = gross_amount - fee_amount
        BigDecimal netAmount = grossAmount.subtract(feeAmount);

        // 환불(REFUND)인 경우 아티스트 정산액에서 차감하기 위해 음수로 변환
        if ("REFUND".equals(dto.getType())) {
            grossAmount = grossAmount.negate();
            feeAmount = feeAmount.negate();
            netAmount = netAmount.negate();
            log.info("환불 차감 금액(netAmount): {}", netAmount);
        } else {
            log.info("결제 정산 금액(netAmount): {}", netAmount);
        }

        log.info("netAmount = 실제 정산금액: {}", netAmount);
        // 5. 정산 내역 원장 기록
        Ledger ledger = Ledger.builder()
                .artistId(dto.getArtistId())
                .orderId(dto.getOrderId())
                .revenueType(dto.getType())
                .grossAmount(grossAmount)
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .status("COMPLETED") // 기본 상태
                .eventTitle(dto.getEventTitle()) // 내역서 표기용 상세 내용
                .build();

        ledgerRepository.save(ledger);

        // 6. 아티스트 계좌 총 누적액 및 출금 가능 잔액 업데이트
        account.addBalances(netAmount);
    }

    // 해당 아티스트의 총누적금액, 잔액 조회
    @Override
    @Transactional(readOnly = true)
    public ArtistAccount getArtistAccount(Long artistId) {
        // 아티스트 계좌가 있으면 가져오고, 없으면 0원짜리 새 객체라도 보여주기!
        return artistAccountRepository.findById(artistId)
            .orElseGet(() -> ArtistAccount.builder()
                .artistId(artistId)
                .totalBalance(BigDecimal.ZERO)
                .withdrawableBalance(BigDecimal.ZERO)
                .build());
    }

    // 유저들의 총 구매횟수, 잔액
    @Override
    @Transactional(readOnly = true)
    public List<UserPaymentSummaryDTO> getUserPaymentSummary(List<Long> memberId) {
        log.info("유저 {}명의 결제 및 잔액 요약 정보 수색 시작! ㅡㅡ🚔", memberId.size());

        return memberId.stream().map(mid -> {
            return walletRepository.findByMemberId(mid)
                .map(wallet -> {
                    int count = transactionHistoryRepository.countByWalletIdAndTransactionType(
                        wallet.getWalletId(), "PAYMENT"
                    );

                    return UserPaymentSummaryDTO.builder()
                        .memberId(mid)
                        .purchaseCount(count)
                        .balance(wallet.getBalance().longValue())
                        .version(wallet.getVersion())
                        .build();
                }).orElse(new UserPaymentSummaryDTO(mid, 0, 0L, 0));
        }).collect(Collectors.toList());
    }

    @Override
    public UserDetailPaymentResponseDTO getUserPaymentDetail(Long memberId){
        log.info("유저 ID {}의 상세 결제/포인트 내역 수색 중... 🔍", memberId);

        // 지갑 조회
        Wallet wallet = walletRepository.findByMemberId(memberId).orElse(null);
        
        if (wallet == null) {
        return UserDetailPaymentResponseDTO.builder()
                .totalPurchases(0)
                .pointBalance(0L)
                .purchaseHistory(Collections.emptyList())
                .pointHistory(Collections.emptyList())
                .build();
    }

        // 모든 트랜잭션 히스토리 최신순으로 가져오기
        List<TransactionHistory> allHistories = transactionHistoryRepository
            .findAllByWalletIdOrderByCreatedAtDesc(wallet.getWalletId());
        
        // PAYMENT이력만 담아오기
        List<PurchaseHistoryDTO> purchaseHistory = allHistories.stream()
            .filter(h -> "PAYMENT".equals(h.getTransactionType()))
            .map(h -> PurchaseHistoryDTO.builder()
                .purchasedAt(h.getCreatedAt().toString())
                .itemName(h.getDescription())
                .amount(h.getAmount().longValue())
                .quantity(h.getQuantity())
                .status(h.getTransactionType())
                .build()).toList();
        
        // 포인트 내역 담아오기
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