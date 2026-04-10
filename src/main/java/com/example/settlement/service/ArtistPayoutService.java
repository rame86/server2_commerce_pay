// src/main/java/com/example/settlement/service/ArtistSettlementService.java
package com.example.settlement.service;

import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.settlement.dto.ArtistDonationResponse;
import com.example.settlement.dto.ArtistPayoutResponseDTO;

/**
 * [아티스트 정산 관리 서비스 인터페이스]
 * 아티스트의 수익 현황과 정산 흐름을 분석하여 대시보드 전용 데이터를 제공함.
 * 여러 도메인에 흩어진 정산 원장(Ledger)과 계좌 상태를 통합하여 인사이트를 산출함.
 */
public interface ArtistPayoutService {

    /**
     * [아티스트 정산 대시보드 데이터 종합 조회]
     * 특정 아티스트의 누적 수익, 이번 달 예상 정산액, 정산 완료 건수 등을 집계함.
     * 프론트엔드 차트 렌더링을 위한 월별 수익 트렌드 및 수익 구성 비중 데이터를 포함함.
     * * @param artistId 조회 대상 아티스트 고유 식별자
     * 
     * @return 요약 카드, 월별 추이, 수익 비중, 상세 내역이 포함된 통합 응답 객체
     */
    ArtistPayoutResponseDTO getSettlementDashboard(Long artistId);

    // [아티스트 후원 내역 조회] 수민 수정
    ArtistDonationResponse artistDonation(Long artistId);

        /**
     * [아티스트 정산 요청 처리]
     * 주기적 정산이나 수동 정산 요청 이벤트 발생 시 호출됨.
     * 확정된 수익 내역을 계산하여 아티스트의 실제 출금 가능 잔액으로 전환함.
     */
    void processArtistSettlementRequest(PaymentEventRequestDTO dto);

    /**
     * [아티스트 지갑 생성 처리]
     * 새로운 아티스트가 시스템에 등록(가입/승인)되었을 때 호출됨.
     * 해당 아티스트가 정산금을 받을 수 있도록 전용 계좌(ArtistAccount)를 초기화함.
     */
    void processArtistWalletCreate(PaymentEventRequestDTO dto);
}