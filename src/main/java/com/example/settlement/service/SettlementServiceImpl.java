// src/main/java/com/example/payment/service/settlement/SettlementServiceImpl.java
package com.example.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.ArtistAccount;
import com.example.payment.domain.Ledger;
import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.repository.ArtistAccountRepository;
import com.example.payment.repository.LedgerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    // 아티스트 정산과 관련된 도메인 레포지토리만 유지
    private final LedgerRepository ledgerRepository;
    private final ArtistAccountRepository artistAccountRepository;

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

        // 3. 아티스트 계좌 조회 (없으면 신규 생성)
        ArtistAccount account = artistAccountRepository.findById(dto.getArtistId())
                .orElseGet(() -> {
                    ArtistAccount newAccount = ArtistAccount.builder()
                            .artistId(dto.getArtistId())
                            .build(); 
                    return artistAccountRepository.save(newAccount);
                });

        // 4. 정산 금액 계산
        BigDecimal baseAmount = dto.getOriginalPrice().multiply(BigDecimal.valueOf(dto.getQuantity()));
        BigDecimal grossAmount = baseAmount.abs();

        // 플랫폼 수수료 계산
        BigDecimal feeRate = dto.getFee() != null
                ? dto.getFee().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal feeAmount = grossAmount.multiply(feeRate);
        
        // 아티스트 실제 정산액
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

        // 5. 정산 내역 원장 기록
        Ledger ledger = Ledger.builder()
                .artistId(dto.getArtistId())
                .orderId(dto.getOrderId())
                .revenueType(dto.getType())
                .grossAmount(grossAmount)
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .status("false") 
                .eventTitle(dto.getEventTitle()) 
                .build();

        ledgerRepository.save(ledger);

        // 6. 아티스트 계좌 총 누적액 및 출금 가능 잔액 업데이트
        account.addBalances(netAmount);
    }

    // 아티스트가 직접 본인의 총누적금액, 잔액 조회
    @Override
    @Transactional(readOnly = true)
    public ArtistAccount getArtistAccount(Long artistId) {
        return artistAccountRepository.findById(artistId)
            .orElseGet(() -> ArtistAccount.builder()
                .artistId(artistId)
                .totalBalance(BigDecimal.ZERO)
                .withdrawableBalance(BigDecimal.ZERO)
                .build());
    }
}