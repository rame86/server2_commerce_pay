// src/main/java/com/example/payment/service/Event/SettlementEventService.java
package com.example.admin.service;

import java.util.List;

import com.example.admin.dto.admin.response.AdminDashboardResponseDTO;
import com.example.admin.dto.admin.response.UserDetailPaymentResponseDTO;
import com.example.admin.dto.admin.response.UserPaymentSummaryDTO;
import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.settlement.entity.ArtistAccount;
import com.example.wallet.dto.WalletDTO;

/**
 * [정산 및 관리자 이벤트 처리 서비스 인터페이스]
 * 관리자 대시보드 통계 집계 및 전용 상세 데이터 조회를 수행함.
 * (순수 비즈니스 로직 담당: 조회/가공된 데이터를 반환하며, 실제 MQ 상태 제어 및 발송은 PaymentEventServiceImpl에 위임)
 */
public interface AdminSettlementEventService {

    /**
     * [관리자 정산 대시보드 데이터 집계]
     * 당월 전체 매출, 플랫폼 수수료, 정산 예정액 등 거시적 통계 데이터를 산출함.
     * 아티스트별 정산 현황 리스트를 포함하여 대시보드 메인 화면 구성을 지원함.
     * * @return AdminDashboardResponseDTO 대시보드 요약 통계 및 아티스트 정산 리스트 데이터
     */
    AdminDashboardResponseDTO processAdminSettlement(PaymentEventRequestDTO dto);

    /**
     * [전체 지갑 목록 조회]
     * 시스템 내에 존재하는 모든 사용자 지갑의 상태와 잔액 정보를 일괄 조회함.
     * 관리자의 전체 자산 모니터링 기능을 수행함.
     * * @return List<WalletDTO> 전체 사용자 지갑 정보 리스트
     */
    List<WalletDTO> processAdminGetAll(PaymentEventRequestDTO dto);

    /**
     * [아티스트 정산 상세 조회]
     * 특정 아티스트의 정산 계좌 상태, 누적 수익, 정산 가능 금액 등 상세 정보를 조회함.
     * 개별 아티스트와의 정산 관련 CS 및 관리에 활용됨.
     * * @return ArtistAccount 아티스트 상세 정산 계좌(원장) 정보
     */
    ArtistAccount processAdminArtistDetail(PaymentEventRequestDTO dto);

    /**
     * [유저 결제 요약 일괄 조회]
     * 여러 명의 사용자에 대해 각 유저별 총 구매 횟수 및 현재 잔액 스냅샷을 조회함.
     * 사용자 목록 페이지에서 결제 상태 요약을 노출할 때 사용됨.
     * * @return List<UserPaymentSummaryDTO> 요청된 유저들의 결제 요약 정보 리스트
     */
    List<UserPaymentSummaryDTO> processAdminSummary(PaymentEventRequestDTO dto);

    /**
     * [유저 상세 결제 내역 조회]
     * 특정 유저의 상세 상품 구매 이력과 포인트 변동(충전/사용) 히스토리를 모두 조회함.
     * 개별 유저의 활동 분석 및 결제 관련 문의 대응을 위한 상세 데이터를 제공함.
     * * @return UserDetailPaymentResponseDTO 특정 유저의 상품 구매 이력 및 포인트 변동 내역
     */
    UserDetailPaymentResponseDTO processAdminUserDetail(PaymentEventRequestDTO dto);

}