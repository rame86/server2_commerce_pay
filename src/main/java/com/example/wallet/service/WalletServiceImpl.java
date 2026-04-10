// src/main/java/com/example/payment/service/WalletServiceImpl.java
package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.payment.dto.service.TransactionDTO;
import com.example.payment.dto.user.PaymentHistoryResponseDTO;
import com.example.payment.entity.TransactionHistory;
import com.example.payment.repository.TransactionHistoryRepository;
import com.example.wallet.dto.WalletDTO;
import com.example.wallet.entity.Wallet;
import com.example.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [지갑 비즈니스 로직 구현체]
 * 포인트 차감/적립, 결제 및 환불 이력 관리, 캐시(Redis) 잔액 동기화를 수행함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final StringRedisTemplate redisTemplate;
    private final TransactionHistoryRepository transactionHistoryRepository;

    /**
     * [전체 지갑 조회]
     * 시스템에 등록된 모든 지갑 정보를 DTO 리스트로 반환함.
     */
    @Override
    @Transactional(readOnly = true)
    public List<WalletDTO> getAllWallets() {
        log.info(">>> [WALLET_ALL] 전체 지갑 목록 조회 요청");
        // DB에서 조회한 Wallet 엔티티 리스트를 map을 통해 순회하며 외부 응답용 DTO로 모두 변환함
        return walletRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * [현재 잔액 조회]
     * 특정 회원의 실시간 지갑 잔액을 반환함.
     */
    @Override
    @Transactional
    public BigDecimal getBalance(Long memberId) {
        log.info(">>> [WALLET_BALANCE] 잔액 확인 요청 - MemberId: {}", memberId);
        return getOrCreateWallet(memberId).getBalance();
    }

    /**
     * [거래 내역 조회]
     * 사용자의 현재 지갑 잔액과 과거 거래 내역을 모두 반환.
     */
    @Override
    @Transactional
    public PaymentHistoryResponseDTO getPaymentHistory(Long memberId) {

        // 1. 지갑 조회. 회원의 지갑이 아직 없다면 초기 잔액 0원인 지갑을 즉시 생성 (Lazy Init)
        Wallet wallet = getOrCreateWallet(memberId);

        // 2. 해당 지갑의 거래 내역을 최신순(내림차순)으로 모두 조회
        List<TransactionHistory> histories = transactionHistoryRepository
                .findAllByWalletIdOrderByCreatedAtDesc(wallet.getWalletId());

        // 3. Entity 객체를 외부 노출용 DTO로 변환 (엔티티 직접 노출 방지)
        // DB 구조가 그대로 반영된 TransactionHistory 엔티티 대신, 클라이언트에게 필요한 정보만 담은 TransactionDTO로 매핑함
        List<TransactionDTO> transactionDTOs = histories.stream()
                .map(this::convertToTransactionDTO)
                .toList();

        // 4. 현재 잔액과 거래 내역 리스트를 묶어서 반환
        return PaymentHistoryResponseDTO.builder()
                .currentBalance(wallet.getBalance())
                .transactions(transactionDTOs)
                .build();
    }

    /**
     * [지갑 조회 및 자동 생성 (Internal)]
     * 지갑이 존재하지 않는 회원의 경우, 초기 잔액 0원인 지갑을 즉시 생성함.
     */
    private Wallet getOrCreateWallet(Long memberId) {
        log.info(">>> [WALLET_GET_OR_CREATE] 지갑 정보 확인 - MemberId: {}", memberId);
        return walletRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    log.info(">>> [WALLET_CREATE] 신규 지갑 자동 생성 - MemberId: {}", memberId);
                    Wallet newWallet = Wallet.builder()
                            .memberId(memberId)
                            .balance(BigDecimal.ZERO)
                            .status("ACTIVE")
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    /**
     * [결제 처리 (포인트 차감)]
     * 주문 이벤트를 수신하여 사용자의 포인트를 차감하고 거래 이력을 기록함.
     * @return TransactionDTO 생성된 결제 원장 데이터
     */
    @Override
    @Transactional
    public TransactionDTO processPayment(PaymentEventRequestDTO dto) {
        log.info(">>> [PAYMENT_PROCESS] 결제 포인트 차감 시작 - OrderId: {}, Amount: {}",
                dto.getOrderId(), dto.getAmount());

        // 1. 지갑 확보 및 상태 검증
        Wallet wallet = getOrCreateWallet(dto.getMemberId());
        if (!"ACTIVE".equals(wallet.getStatus())) {
            log.error(">>> [PAYMENT_FAIL] 지갑 비활성 상태 - WalletId: {}", wallet.getWalletId());
            throw new IllegalStateException("유효하지 않은 지갑 상태입니다.");
        }

        // 2. 잔액 검증 및 차감 (낙관적 락 적용됨)
        // Entity 내부의 deductBalance 메서드에서 잔액 부족 체크 수행
        // 외부에서 Setter를 쓰지 못하게 막고, 오직 검증된 entity 내부의 deductBalance를 통해서만 상태를 바꾸도록 보호(캡슐화)
        wallet.deductBalance(dto.getAmount());

        // 3. 거래 이력(원장) 저장
        String paymentDesc = "결제 - " + (dto.getEventTitle() != null ? dto.getEventTitle() : "상품 결제");
        TransactionHistory savedHistory = recordTransaction(wallet, dto, dto.getAmount().negate(), paymentDesc);

        log.info(">>> [PAYMENT_SUCCESS] 결제 차감 완료 - New Balance: {}", wallet.getBalance());
        
        // 4. 저장 완료된 엔티티를 DTO로 변환하여 반환
        // DB에 기록되어 식별자(PK)와 생성 시간(createdAt)이 모두 부여된 '최종 데이터'를 DTO로 매핑하여 응답함
        return convertToTransactionDTO(savedHistory);
    }

    /**
     * [충전,환불 처리 (포인트 가산)]
     * 결제 취소 이벤트를 수신하여 차감되었던 금액을 지갑에 다시 복구함.
     */
    @Override
    @Transactional
    public TransactionDTO processRefund(PaymentEventRequestDTO dto) {
        log.info(">>> [REFUND_PROCESS] 환불 처리 시작 - OrderId: {}", dto.getOrderId());

        // 1. 원본 결제 내역 존재 여부 확인
        TransactionHistory paymentTx = transactionHistoryRepository
                .findTopByReferenceIdAndTransactionType(dto.getOrderId(), "PAYMENT");
        if (paymentTx == null) {
            log.error(">>> [REFUND_FAIL] 원본 결제 데이터 없음 - OrderId: {}", dto.getOrderId());
            throw new IllegalArgumentException("원본 결제 내역을 찾을 수 없습니다.");
        }

        // 2. 멱등성 검증 (중복 환불 방지 및 기존 내역 반환)
        TransactionHistory existingRefund = transactionHistoryRepository
                .findTopByReferenceIdAndTransactionType(dto.getOrderId(), "REFUND");
        if (existingRefund != null) {
            log.warn(">>> [REFUND_SKIP] 이미 완료된 환불 요청 - OrderId: {}", dto.getOrderId());
            // 이미 처리된 환불건이라도, 요청 측에 일관된 응답을 주기 위해 기존 엔티티를 DTO로 변환하여 반환함
            return convertToTransactionDTO(existingRefund);
        }

        // 3. 금액 복구
        // 외부에서 Setter를 쓰지 못하게 막고, 오직 검증된 entity 내부의 addBalance를 통해서만 상태를 바꾸도록 보호(캡슐화)
        BigDecimal refundAmount = paymentTx.getAmount().abs(); // 저장된 음수 금액을 양수로 변환
        Wallet wallet = walletRepository.findById(paymentTx.getWalletId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        wallet.addBalance(refundAmount);

        // 4. 환불 이력 저장
        // 원본 결제 내역의 description에서 "결제 - " 접두사를 제거 후 "환불 - " 접두사로 대체
        String originalDesc = paymentTx.getDescription() != null ? paymentTx.getDescription() : "결제 취소";
        String refundDesc = originalDesc.startsWith("결제 - ")
                ? "환불 - " + originalDesc.substring("결제 - ".length())
                : "환불 - " + originalDesc;

        TransactionHistory savedHistory = recordTransaction(wallet, dto, refundAmount, refundDesc);

        log.info(">>> [REFUND_SUCCESS] 포인트 복구 완료 - Refund: {}, New Balance: {}", refundAmount, wallet.getBalance());
        
        // 5. 생성된 환불 원장 데이터를 DTO로 변환하여 반환
        return convertToTransactionDTO(savedHistory);
    }

    /**
     * [거래 이력 기록 공통 로직]
     * 메모리 상에 생성한 거래 내역 객체를 DB에 실제로 INSERT 하는 역할.
     * * @return TransactionHistory DB에 저장되어 ID와 자동 생성 시간 등이 모두 부여된 최종 거래 원장 엔티티
     */
    private TransactionHistory recordTransaction(Wallet wallet, PaymentEventRequestDTO dto, BigDecimal actualAmount,
            String description) {
        log.debug(">>> [TX_RECORD] 거래 내역 생성 중 - RefId: {}, Type: {}", dto.getOrderId(), dto.getType());

        TransactionHistory th = TransactionHistory.builder()
                .walletId(wallet.getWalletId())
                .transactionType(dto.getType())
                .amount(actualAmount) // 결제(-), 환불(+) 계산된 최종 금액
                .balanceAfter(wallet.getBalance()) // 이미 업데이트된 지갑의 최신 잔액
                .referenceId(dto.getOrderId())
                .description(description) // 이벤트에 따라 적절한 설명 생성
                .originalAmount(dto.getOriginalPrice()) // 원래 결제 금액 (결제, 환불 시 참고용)
                .fee(dto.getFee())
                .shippingFee(dto.getShippingFee())
                .quantity(dto.getQuantity())
                .artistId(dto.getArtistId())
                .build();

        // save() 호출 시점에 DB INSERT 쿼리가 발생하며, 반환되는 객체는 DB로부터 PK(고유 ID)를 발급받은 '확정 데이터'임
        return transactionHistoryRepository.save(th);
    }

    /**
     * [엔티티 -> DTO 변환 공통 로직 (원장)]
     * DB에서 갓 저장되었거나 조회된 TransactionHistory 엔티티를 TransactionDTO 객체로 변환함.
     * 엔티티의 모든 속성(내부 식별자 등)을 노출하지 않고, 실제 서비스 간 통신이나 응답에 필요한 핵심 데이터만 추려냄.
     */
    private TransactionDTO convertToTransactionDTO(TransactionHistory history) {
        return TransactionDTO.builder()
                .transactionType(history.getTransactionType())
                .amount(history.getAmount())
                .balanceAfter(history.getBalanceAfter())
                .description(history.getDescription())
                .createdAt(history.getCreatedAt())
                .build();
    }

    /**
     * [엔티티 -> DTO 변환 공통 로직 (지갑)]
     * 원본 Wallet 엔티티를 외부 노출용 WalletDTO로 변환함.
     * DB 스키마 변경이 발생하더라도 외부 API나 통신 규격(DTO)에는 영향을 주지 않도록 결합도를 낮추는 역할.
     * * @param wallet 변환할 Wallet 엔티티
     * @return WalletDTO 변환된 DTO 객체
     */
    private WalletDTO convertToResponseDTO(Wallet wallet) {
        return WalletDTO.builder()
                .walletId(wallet.getWalletId())
                .memberId(wallet.getMemberId())
                .balance(wallet.getBalance())
                .status(wallet.getStatus())
                .version(wallet.getVersion() != null ? wallet.getVersion() : 0)
                .build();
    }

    /**
     * [Redis 잔액 캐시 동기화]
     * DB 트랜잭션 성공 후 호출되어, 인증 서비스(Core) 등에서 활용하는 잔액 캐시 정보를 업데이트함.
     */
    @Override
    public void updateRedisBalance(Long memberId, BigDecimal balance) {
        log.info(">>> [REDIS_SYNC] 잔액 캐시 업데이트 - MemberId: {}, Balance: {}", memberId, balance);
        redisTemplate.opsForHash().put("AUTH:MEMBER:" + memberId, "balance", balance.toPlainString());
    }
}