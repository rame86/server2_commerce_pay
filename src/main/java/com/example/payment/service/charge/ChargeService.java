// src/main/java/com/example/payment/service/charge/ChargeService.java
package com.example.payment.service.charge;

import java.util.UUID;

import com.example.payment.dto.request.ChargeRequestDTO;
import com.example.payment.dto.response.ChargeReadyResponseDTO;
import com.example.payment.dto.response.PaymentHistoryResponseDTO;

/**
 * [포인트 충전 및 조회 서비스 인터페이스]
 * 지갑 생성, 충전 준비, 최종 결제 승인 등 자산 관리의 핵심 기능을 규정함.
 */
public interface ChargeService {

    /**
     * [지갑 및 거래 내역 조회]
     * 사용자의 현재 잔액과 전체 거래 히스토리를 반환함.
     * * 특징: 지갑이 존재하지 않는 신규 회원의 경우, 지갑을 자동으로 생성 후 결과를 반환함.
     * @param memberId 사용자 식별 ID
     * @return 현재 잔액 및 상세 거래 내역 리스트
     */
    PaymentHistoryResponseDTO getPaymentHistory(Long memberId);

    /**
     * [포인트 충전 준비 (Ready)]
     * PG사 결제 요청 전, 내부 원장을 'PENDING' 상태로 생성하고 외부 TID를 발급받음.
     * @param memberId 사용자 식별 ID
     * @param request  충전 금액 및 수단 정보 (PayType 등)
     * @param token    사용자 인증 토큰
     * @return 결제창 리다이렉트 URL 및 TID 정보를 포함한 응답 객체
     */
    ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request, String token);

    /**
     * [최종 결제 승인 (Approve)]
     * 사용자가 PG사 인증을 마친 후 전달된 토큰을 기반으로 실제 결제를 확정함.
     * * 처리 로직: PG 승인 완료 시 내부 원장 상태를 'SUCCESS'로 변경하고 실제 지갑 잔액을 가산함.
     * @param chargeId 내부 결제 원장 UUID
     * @param pgToken  PG사로부터 발급받은 인증 토큰 (예: pg_token)
     * @param memberId 사용자 식별 ID
     */
    void approvePayment(UUID chargeId, String pgToken, String memberId);

}