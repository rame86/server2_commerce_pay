// src/main/java/com/example/settlement/service/SettlementService.java
package com.example.settlement.service;

import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.settlement.entity.ArtistAccount;

/**
 * [정산 처리 서비스 인터페이스]
 * 결제 완료 및 환불 이벤트를 기반으로 아티스트의 수익을 계산하고,
 * 정산 원장 기록 및 계좌 잔액 업데이트를 총괄함.
 */
public interface SettlementLedgerService {

    /**
     * [정산 프로세스 실행]
     * 수신된 결제/환불 데이터를 분석하여 플랫폼 수수료를 제외한 순수익을 계산함.
     * * * 주요 역할:
     * 1. 동일 주문번호에 대한 중복 정산 방지 (멱등성 보장)
     * 2. 상품별/유형별 수수료 차등 적용 및 순수익 산출
     * 3. 환불 시 기존 정산액의 역산 처리 (음수 반영)
     * 4. 정산 원장(Ledger) 저장 및 아티스트 계좌 잔액 갱신
     * * @param dto 메시지 큐 등을 통해 전달된 결제 상세 정보
     */
    void processSettlement(PaymentEventRequestDTO dto);

    /**
     * [아티스트 정산 계좌 조회]
     * 특정 아티스트의 실시간 정산 상태를 확인하기 위한 정보를 반환함.
     * * * 제공 정보:
     * 1. 총 누적 매출액 (Total Revenue)
     * 2. 현재 출금 가능 잔액 (Withdrawable Balance)
     * * @param artistId 조회 대상 아티스트의 고유 식별자
     * @return 아티스트의 수익 스냅샷 정보 (계좌가 없을 경우 초기 객체 반환)
     */
    ArtistAccount getArtistAccount(Long artistId);

}