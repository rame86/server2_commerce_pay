// src/main/java/com/example/payment/service/SettlementServiceImpl.java
package com.example.payment.service;

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

    private final LedgerRepository ledgerRepository;
    private final ArtistAccountRepository artistAccountRepository;

    @Override
    @Transactional
    public void processSettlement(PaymentEventDTO dto) {
        // 1. 아티스트 ID가 없는 결제건은 정산 제외 (일반 상품 등)
        if (dto.getArtistId() == null) {
            return;
        }

        // 2. 멱등성 보장: 동일한 주문 번호(order_id)로 이미 정산되었는지 확인 [cite: 226]
        if (ledgerRepository.existsByOrderId(dto.getOrderId())) {
            log.info("이미 정산 처리된 주문입니다. 주문번호: {}", dto.getOrderId());
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

        // 4. 정산 금액 계산
        // 상품 원가 (gross_amount)는 originalPrice 사용 [cite: 193]
        BigDecimal grossAmount = dto.getOriginalPrice() != null ? dto.getOriginalPrice() : dto.getAmount();

        // 플랫폼 수수료 (fee_amount) 계산
        BigDecimal feeRate = dto.getFee() != null
                ? dto.getFee().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal feeAmount = grossAmount.multiply(feeRate);

        // 아티스트 실제 정산액 (net_amount) = gross_amount - fee_amount [cite: 195]
        BigDecimal netAmount = grossAmount.subtract(feeAmount);

        // 5. 정산 내역 원장 기록 [cite: 188]
        Ledger ledger = Ledger.builder()
                .artistId(dto.getArtistId())
                .orderId(dto.getOrderId())
                .revenueType(dto.getType())
                .grossAmount(grossAmount)
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .status("COMPLETED") // 기본 상태 [cite: 196]
                .eventTitle(dto.getEventTitle()) // 내역서 표기용 상세 내용 [cite: 197]
                .build();

        ledgerRepository.save(ledger);

        // 6. 아티스트 계좌 총 누적액 및 출금 가능 잔액 업데이트 [cite: 181, 182]
        account.addBalances(netAmount);
    }
}