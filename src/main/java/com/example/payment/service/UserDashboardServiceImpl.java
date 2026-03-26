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

/**
 * [사용자 결제 대시보드 서비스 구현체]
 * 유저의 현재 잔액, 구매 내역, 포인트 변동 이력을 통합하여 조회함.
 * 모든 조회 로직은 @Transactional(readOnly = true)를 통해 성능 최적화됨.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDashboardServiceImpl implements UserDashboardService {

    private final WalletRepository walletRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;

    /**
     * [유저 결제 상세 대시보드 데이터 조회]
     * 지갑 정보와 거래 내역을 취합하여 프론트엔드 대시보드 구성에 필요한 통합 DTO를 반환함.
     * @param memberId 사용자 식별 ID
     * @return 잔액, 총 구매수, 상세 이력이 포함된 통합 응답 객체
     */
    @Override
    public UserDetailPaymentResponseDTO getUserDashboardDetail(Long memberId) {
        log.info(">>> [USER_DASHBOARD] 유저 상세 대시보드 데이터 조회 시작 - MemberId: {}", memberId);
                
        // 1. 지갑 조회
        Wallet wallet = walletRepository.findByMemberId(memberId).orElse(null);

        // 2. 지갑이 없는 경우 예외 처리 (Early Return 패턴 적용)
        // 신규 유저 등 지갑이 아직 생성되지 않은 경우 빈 데이터를 안전하게 반환함.
        if (wallet == null) {
            log.warn(">>> [USER_DASHBOARD] 유저 ID {}의 지갑 정보가 존재하지 않음 - 빈 데이터 반환", memberId);
            return createEmptyResponse();
        }

        // 3. 전체 거래 내역 조회 (최신순 정렬)
        List<TransactionHistory> allHistories = transactionHistoryRepository
                .findAllByWalletIdOrderByCreatedAtDesc(wallet.getWalletId());

        // 4. 구매 내역(PAYMENT)만 필터링하여 DTO 변환
        List<PurchaseHistoryDTO> purchaseHistory = allHistories.stream()
                .filter(h -> "PAYMENT".equals(h.getTransactionType()))
                .map(this::toPurchaseHistoryDTO)
                .toList();

        // 5. 전체 포인트 변동 내역(충전, 사용, 환불 등) DTO 변환
        List<PointHistoryDTO> pointHistory = allHistories.stream()
                .map(this::toPointHistoryDTO)
                .toList();

        log.info(">>> [USER_DASHBOARD] 조회 및 변환 완료 - MemberId: {}, 총 구매: {}건, 잔액: {}", 
                 memberId, purchaseHistory.size(), wallet.getBalance());

        // 6. 최종 결과 조립 및 반환
        return UserDetailPaymentResponseDTO.builder()
                .totalPurchases(purchaseHistory.size())
                .pointBalance(wallet.getBalance() != null ? wallet.getBalance().longValue() : 0L)
                .purchaseHistory(purchaseHistory)
                .pointHistory(pointHistory)
                .build();
    }

    /**
     * 지갑 정보가 없는 유저를 위한 초기화된 빈 응답 객체 생성
     */
    private UserDetailPaymentResponseDTO createEmptyResponse() {
        log.debug(">>> [DASHBOARD_UTIL] 빈 응답 데이터 생성");
        return UserDetailPaymentResponseDTO.builder()
                .totalPurchases(0)
                .pointBalance(0L)
                .purchaseHistory(Collections.emptyList())
                .pointHistory(Collections.emptyList())
                .build();
    }

    /**
     * TransactionHistory 엔티티를 PurchaseHistoryDTO(상품 구매 관점)로 변환
     */
    private PurchaseHistoryDTO toPurchaseHistoryDTO(TransactionHistory h) {
        log.debug(">>> [DASHBOARD_UTIL] 구매 내역 DTO 변환 - TxId: {}", h.getTransactionId());
        return PurchaseHistoryDTO.builder()
                .purchasedAt(h.getCreatedAt().toString())
                .itemName(h.getDescription())
                .amount(h.getAmount() != null ? h.getAmount().longValue() : 0L)
                .quantity(h.getQuantity())
                .status(h.getTransactionType())
                .build();
    }

    /**
     * TransactionHistory 엔티티를 PointHistoryDTO(자산 흐름 관점)로 변환
     */
    private PointHistoryDTO toPointHistoryDTO(TransactionHistory h) {
        log.debug(">>> [DASHBOARD_UTIL] 포인트 이력 DTO 변환 - TxId: {}", h.getTransactionId());
        return PointHistoryDTO.builder()
                .processedAt(h.getCreatedAt().toString())
                .type(h.getTransactionType())
                .amount(h.getAmount() != null ? h.getAmount().longValue() : 0L)
                .description(h.getDescription())
                .balanceAfter(h.getBalanceAfter() != null ? h.getBalanceAfter().longValue() : 0L)
                .build();
    }
}