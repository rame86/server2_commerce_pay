// src/main/java/com/example/payment/service/charge/ChargeService.java
package com.example.payment.service.charge;

import java.util.UUID;

import com.example.payment.dto.user.ChargeReadyResponseDTO;
import com.example.payment.dto.user.ChargeRequestDTO;

/**
 * [포인트 충전 및 조회 서비스 인터페이스]
 * 지갑 생성, 충전 준비, 최종 결제 승인 등 자산 관리의 핵심 기능을 규정함.
 */
public interface ChargeService {

    /**
     * [결제 충전 준비]
     * PG사 결제창을 띄우기 전, 내부 시스템에 결제 원장을 '대기(PENDING)' 상태로 생성하고
     * PG사의 TID를 발급받아 원장에 매핑한 후, 클라이언트에게 결제창 URL과 함께 응답하는 단계.     
     * @param memberId 사용자 식별 ID
     * @param request  충전 금액 및 수단 정보 (PayType 등)     
     * @return 결제창 리다이렉트 URL 및 TID 정보를 포함한 응답 객체
     */
    ChargeReadyResponseDTO readyPayment(Long memberId, ChargeRequestDTO request);

    /**
     * [결제 승인 처리]
     * 사용자가 PG사 결제창에서 인증을 마친 후 리다이렉트 되었을 때,
     * 실제 금액 출금을 위해 PG사에 '최종 승인'을 요청하는 단계.
     * 최종 승인이 완료되면 내부 원장 상태를 '성공(SUCCESS)'으로 변경하고, 사용자 지갑에 충전 금액을 반영하며, 거래 내역을 기록하는 후속 처리를 수행함.
     * @param chargeId 내부 결제 원장 UUID
     * @param pgToken  PG사로부터 발급받은 인증 토큰 (예: pg_token)
     * @param memberId 사용자 식별 ID
     */
    void approvePayment(UUID chargeId, String pgToken, String memberId);

}