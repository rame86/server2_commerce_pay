// src/main/java/com/example/payment/service/WalletService.java
package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.List;

import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.payment.dto.service.TransactionDTO;
import com.example.payment.dto.user.PaymentHistoryResponseDTO;
import com.example.wallet.dto.WalletDTO;

/**
 * [지갑 관리 서비스 인터페이스]
 * 사용자의 포인트 잔액 조회, 결제/환불에 따른 차감 및 복구 로직을 정의함.
 * 데이터베이스(RDB)와 캐시(Redis) 간의 잔액 정합성을 유지하는 역할을 수행.
 */
public interface WalletService {

    /**
     * [전체 지갑 조회]
     * 시스템에 등록된 모든 사용자의 지갑 상태 및 잔액 목록을 반환함 (주로 관리자 기능).
     * @return 지갑 상세 정보(WalletDTO) 리스트
     */
    List<WalletDTO> getAllWallets();
    
    /**
     * [현재 잔액 조회]
     * 특정 회원의 현재 사용 가능한 포인트를 조회함.
     * 특징: 해당 회원의 지갑이 존재하지 않을 경우, 기본 잔액 0원인 지갑을 자동 생성함.
     * @param memberId 회원 식별 ID
     * @return 현재 보유 잔액 (BigDecimal)
     */
    BigDecimal getBalance(Long memberId);
    
    /**
     * [거래 내역 조회]
     * 사용자의 현재 잔액과 전체 거래 히스토리를 반환함.
     * * 특징: 지갑이 존재하지 않는 신규 회원의 경우, 지갑을 자동으로 생성 후 결과를 반환함.
     * @param memberId 사용자 식별 ID
     * @return 현재 잔액 및 상세 거래 내역 리스트
     */
    PaymentHistoryResponseDTO getPaymentHistory(Long memberId);

    /**
     * [결제 처리: 포인트 차감]
     * 결제 완료 이벤트를 수신하여 사용자의 잔액을 차감하고 거래 이력을 원장에 기록함.
     * 로직: 지갑 상태 확인 -> 잔액 검증 -> 차감(낙관적 락 적용) -> 이력 저장
     * @param dto 결제 상세 정보가 담긴 이벤트 객체
     */
    TransactionDTO processPayment(PaymentEventRequestDTO dto);

    /**
     * [환불 처리: 포인트 복구]
     * 취소/환불 이벤트를 수신하여 차감되었던 금액을 다시 지갑으로 반환함.
     * 로직: 원본 결제 확인 -> 중복 환불 체크(멱등성) -> 잔액 복구 -> 환불 이력 저장
     * @param dto 환불 상세 정보가 담긴 이벤트 객체
     */
    TransactionDTO processRefund(PaymentEventRequestDTO dto);

    /**
     * [Redis 캐시 잔액 업데이트]
     * DB에서 변경된 최종 잔액을 Redis의 회원 정보(Hash)에 동기화함.
     * 목적: Core 서비스나 인증 서비스에서 빠른 잔액 조회를 가능케 함.
     * @param memberId 회원 식별 ID
     * @param balance  동기화할 최종 잔액
     */
    void updateRedisBalance(Long memberId, BigDecimal balance);
 
    
}