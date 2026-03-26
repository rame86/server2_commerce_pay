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

/**
 * [정산 처리 서비스 구현체]
 * 결제 완료 또는 환불 이벤트를 수신하여 아티스트별 정산 원장을 기록하고
 * 실시간 정산 계좌 잔액을 업데이트함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final LedgerRepository ledgerRepository;
    private final ArtistAccountRepository artistAccountRepository;

    /**
     * [정산 프로세스 실행]
     * 결제/환불 데이터를 바탕으로 수수료를 계산하여 정산 원장을 생성하고 아티스트 잔액에 반영함.
     */
    @Override
    @Transactional
    public void processSettlement(PaymentEventDTO dto) {
        log.info(">>> [SETTLEMENT] 정산 처리 시작 - OrderId: {}, Type: {}", dto.getOrderId(), dto.getType());

        // 1. 아티스트 식별자 검증 (아티스트와 무관한 일반 상품 결제건은 정산 대상에서 제외)
        if (dto.getArtistId() == null) {
            log.info(">>> [SETTLEMENT] 아티스트 정보가 없는 결제건으로 정산 스킵 - OrderId: {}", dto.getOrderId());
            return;
        }

        // 2. 멱등성(Idempotency) 검증
        // 동일한 주문번호와 유형(PAYMENT/REFUND)으로 이미 처리된 원장이 있다면 중복 처리를 방지함.
        if (ledgerRepository.existsByOrderIdAndRevenueType(dto.getOrderId(), dto.getType())) {
            log.warn(">>> [SETTLEMENT] 이미 처리된 정산 내역 존재 - OrderId: {}, Type: {}", dto.getOrderId(), dto.getType());
            return;
        }

        // 3. 아티스트 계좌 확보
        // 최초 수익 발생 시 계좌가 없을 수 있으므로 orElseGet을 통해 자동 생성(Lazy Init) 처리함.
        ArtistAccount account = artistAccountRepository.findById(dto.getArtistId())
                .orElseGet(() -> {
                    log.info(">>> [SETTLEMENT] 아티스트 계좌 신규 생성 - ArtistId: {}", dto.getArtistId());
                    ArtistAccount newAccount = ArtistAccount.builder()
                            .artistId(dto.getArtistId())
                            .build(); 
                    return artistAccountRepository.save(newAccount);
                });

        // 4. 정산 금액 산출 로직
        // 원가 합계 계산 (상품 가격 * 수량)
        BigDecimal baseAmount = dto.getOriginalPrice().multiply(BigDecimal.valueOf(dto.getQuantity()));
        BigDecimal grossAmount = baseAmount.abs(); // 계산 편의를 위해 일단 절대값으로 처리

        // 수수료율 적용 (예: 10% -> 0.10)
        BigDecimal feeRate = dto.getFee() != null
                ? dto.getFee().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        
        // 플랫폼 수수료 및 아티스트 순수익(Net) 계산
        BigDecimal feeAmount = grossAmount.multiply(feeRate).setScale(0, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(feeAmount);

        // 5. 거래 유형에 따른 부호 처리
        // 환불(REFUND)인 경우 아티스트의 누적액에서 차감해야 하므로 모든 금액을 음수로 반전시킴.
        if ("REFUND".equals(dto.getType())) {
            grossAmount = grossAmount.negate();
            feeAmount = feeAmount.negate();
            netAmount = netAmount.negate();
            log.info(">>> [SETTLEMENT] 환불 처리 반영 - 차감 정산액: {}", netAmount);
        } else {
            log.info(">>> [SETTLEMENT] 결제 완료 반영 - 확정 정산액: {}", netAmount);
        }

        // 6. 정산 원장(Ledger) 기록 생성
        Ledger ledger = Ledger.builder()
                .artistId(dto.getArtistId())
                .orderId(dto.getOrderId())
                .revenueType(dto.getType())
                .grossAmount(grossAmount)
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .status("COMPLETED") // 정산 기록 확정 상태
                .eventTitle(dto.getEventTitle()) 
                .build();

        ledgerRepository.save(ledger);

        // 7. 아티스트 실시간 계좌 잔액 업데이트
        // 낙관적 락(Optimistic Lock)이 적용된 엔티티 메서드를 호출하여 누적액 및 출금 가능액 합산.
        account.addBalances(netAmount);
        
        log.info(">>> [SETTLEMENT] 정산 완료 및 잔액 업데이트 성공 - ArtistId: {}, 최종 순익: {}", 
                 dto.getArtistId(), netAmount);
    }

    /**
     * [아티스트 계좌 정보 조회]
     * 아티스트 본인의 누적 수익 및 출금 가능 잔액 스냅샷을 반환함.
     */
    @Override
    @Transactional(readOnly = true)
    public ArtistAccount getArtistAccount(Long artistId) {
        log.info(">>> [ARTIST_ACCOUNT] 계좌 잔액 조회 - ArtistId: {}", artistId);
        
        return artistAccountRepository.findById(artistId)
            .orElseGet(() -> {
                log.info(">>> [ARTIST_ACCOUNT] 조회된 계좌 없음, 기본 객체 반환 - ArtistId: {}", artistId);
                return ArtistAccount.builder()
                    .artistId(artistId)
                    .totalBalance(BigDecimal.ZERO)
                    .withdrawableBalance(BigDecimal.ZERO)
                    .build();
            });
    }
}