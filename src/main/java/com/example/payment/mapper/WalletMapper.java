// src/main/java/com/example/payment/mapper/PaymentMapper.java

package com.example.payment.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.payment.dto.response.WalletResponseDTO;

@Mapper
public interface WalletMapper {

    // 모든 지갑 정보를 조회하는 메서드
    List<WalletResponseDTO> findAllWallets();

    // 타 서비스 연동용: member_id로 잔액 조회
    Long getBalanceByMemberId(@Param("memberId") Long memberId);

    // 결제/환불용: 회원 ID로 지갑 조회 (낙관적 락 version 포함)
    WalletResponseDTO findWalletByMemberId(@Param("memberId") Long memberId);

    // 결제/환불용: 지갑 ID로 지갑 조회
    WalletResponseDTO findWalletById(@Param("walletId") String walletId);

    // 잔액 업데이트 (Long 타입 통일, 낙관적 락 적용)
    int updateWalletBalance(
            @Param("walletId") String walletId,
            @Param("newBalance") Long newBalance,
            @Param("version") Integer version);

    // 트랜잭션 기록 추가
    void insertTransaction(Map<String, Object> transactionData);

    // 원본 결제 트랜잭션 조회 (환불 검증용)
    Map<String, Object> findTransactionByReferenceAndType(
            @Param("referenceId") String referenceId,
            @Param("transactionType") String transactionType);
}