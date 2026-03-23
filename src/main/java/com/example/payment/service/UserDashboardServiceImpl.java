// src/main/java/com/example/payment/service/UserDashboardServiceImpl.java

package com.example.payment.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.domain.TransactionHistory;
import com.example.payment.dto.response.PointHistoryDTO;
import com.example.payment.dto.response.PurchaseHistoryDTO;
import com.example.payment.dto.response.UserDetailPaymentResponseDTO;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.wallet.domain.Wallet;
import com.example.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // [성능 최적화] 읽기 전용 트랜잭션
public class UserDashboardServiceImpl implements UserDashboardService {

    private final WalletRepository walletRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;

    @Override
    public UserDetailPaymentResponseDTO getUserDashboardDetail(Long memberId) {
        // 1. 지갑 조회
        Wallet wallet = walletRepository.findByMemberId(memberId).orElse(null);

        // 2. 지갑이 없는 경우 예외 처리 (Early Return 패턴)
        if (wallet == null) {
            log.warn("[UserDashboard] 유저 ID {}의 지갑 정보 없음 - 빈 데이터 반환", memberId);
            return createEmptyResponse();
        }

        // 3. 거래 내역 조회
        List<TransactionHistory> allHistories = transactionHistoryRepository
                .findAllByWalletIdOrderByCreatedAtDesc(wallet.getWalletId());

        // 4. 구매 내역 변환
        List<PurchaseHistoryDTO> purchaseHistory = allHistories.stream()
                .filter(h -> "PAYMENT".equals(h.getTransactionType()))
                .map(this::toPurchaseHistoryDTO)
                .toList();

        // 5. 전체 포인트 내역 변환
        List<PointHistoryDTO> pointHistory = allHistories.stream()
                .map(this::toPointHistoryDTO)
                .toList();

        log.info("[UserDashboard] 유저 ID {} - 포인트: {}, 구매: {}건", 
                memberId, wallet.getBalance(), purchaseHistory.size());

        // 6. 결과 조립 및 반환
        return UserDetailPaymentResponseDTO.builder()
                .totalPurchases(purchaseHistory.size())
                .pointBalance(wallet.getBalance() != null ? wallet.getBalance().longValue() : 0L)
                .purchaseHistory(purchaseHistory)
                .pointHistory(pointHistory)
                .build();
    }

    // =======================================================
    // [내부 유틸리티 메서드] DTO 변환 및 기본값 생성 로직 분리
    // =======================================================

    private UserDetailPaymentResponseDTO createEmptyResponse() {
        return UserDetailPaymentResponseDTO.builder()
                .totalPurchases(0)
                .pointBalance(0L)
                .purchaseHistory(Collections.emptyList())
                .pointHistory(Collections.emptyList())
                .build();
    }

    private PurchaseHistoryDTO toPurchaseHistoryDTO(TransactionHistory h) {
        return PurchaseHistoryDTO.builder()
                .purchasedAt(h.getCreatedAt().toString())
                .itemName(h.getDescription())
                .amount(h.getAmount() != null ? h.getAmount().longValue() : 0L) // NPE 방지
                .quantity(h.getQuantity())
                .status(h.getTransactionType())
                .build();
    }

    private PointHistoryDTO toPointHistoryDTO(TransactionHistory h) {
        return PointHistoryDTO.builder()
                .processedAt(h.getCreatedAt().toString())
                .type(h.getTransactionType())
                .amount(h.getAmount() != null ? h.getAmount().longValue() : 0L) // NPE 방지
                .description(h.getDescription())
                .balanceAfter(h.getBalanceAfter() != null ? h.getBalanceAfter().longValue() : 0L)
                .build();
    }
}