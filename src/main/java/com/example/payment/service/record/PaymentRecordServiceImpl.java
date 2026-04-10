//src/main/java/com/example/payment/service/PaymentRecordServiceImpl.java
package com.example.payment.service.record;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.entity.Charge;
import com.example.payment.entity.TransactionHistory;
import com.example.payment.repository.ChargeRepository;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.wallet.entity.Wallet;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecordServiceImpl implements PaymentRecordService {

    private final ChargeRepository chargeRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final TransactionHistoryRepository transactionHistoryRepository;

    /**
     * [결제 성공 후속 처리]
     * REQUIRES_NEW: 호출한 쪽(approvePayment)에 트랜잭션이 없더라도 무조건 새로운 트랜잭션을 생성.
     * PG사 결제는 성공했는데 내부 DB 반영 중 에러가 나더라도 상태 일관성을 관리하기 위한 분리.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processApprovalSuccess(UUID chargeId, String memberId) {
        // 1. 원장 상태를 '성공(SUCCESS)'으로 변경
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("원장 조회 실패"));
        charge.success(); // charge 엔티티 내부 로직 이용해 상태 변경 및 성공 시각 기록

        // 2. 실제 사용자 지갑에 결제된 금액만큼 잔액 추가
        Wallet wallet = walletRepository.findById(charge.getWalletId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        wallet.addBalance(charge.getAmount());

        // 3. 거래 내역(History) 스냅샷 기록 (이후 감사 및 내역 조회용)
        TransactionHistory txHistory = TransactionHistory.builder()
                .walletId(wallet.getWalletId())
                .transactionType("CHARGE")
                .amount(charge.getAmount())
                .balanceAfter(wallet.getBalance()) // 금액 추가 후의 최종 잔액 기록
                .referenceId(chargeId.toString())
                .description(charge.getPgProvider() + " 충전")
                .build();
        transactionHistoryRepository.save(txHistory);

        // 4. Redis 캐시 데이터 동기화 (빠른 잔액 조회를 위해 RDB와 상태를 맞춤)
        BigDecimal balance = walletService.getBalance(Long.valueOf(memberId));
        walletService.updateRedisBalance(Long.valueOf(memberId), balance);

        log.info(">>> [APPROVE_PAYMENT] 원장 및 잔액 반영 완료");
    }

    /**
     * [결제 실패 후속 처리]
     * REQUIRES_NEW: 메인 로직에서 예외가 발생해 롤백되더라도, 실패 이력 자체는 무조건 DB에 남기기 위함.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processApprovalFail(UUID chargeId, String errorMessage) {
        chargeRepository.findById(chargeId).ifPresent(charge -> {
            charge.fail(errorMessage); // 원장 상태를 FAIL로 변경하고 사유 기록
        });
    }
}
