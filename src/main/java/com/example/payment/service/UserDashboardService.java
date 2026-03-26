// src/main/java/com/example/payment/service/UserDashboardService.java
package com.example.payment.service;

import com.example.payment.dto.response.UserDetailPaymentResponseDTO;

/**
 * [사용자 결제 대시보드 서비스 인터페이스]
 * 유저의 지갑 잔액, 상품 구매 이력, 포인트 사용/충전 히스토리를 
 * 통합하여 조회하는 기능을 정의함.
 */
public interface UserDashboardService {

    /**
     * [사용자 대시보드 상세 정보 조회]
     * 마이페이지 진입 시 필요한 유저의 전체 결제 지표를 한 번에 집계하여 반환함.
     * * @param memberId 데이터를 조회할 사용자의 식별자
     * @return 총 구매 횟수, 현재 잔액, 구매 리스트, 포인트 리스트가 포함된 통합 DTO
     */
    UserDetailPaymentResponseDTO getUserDashboardDetail(Long memberId);
}