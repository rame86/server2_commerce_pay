// src/main/java/com/example/payment/dto/response/ChargeReadyResponseDTO.java
package com.example.payment.dto.response;

import java.util.UUID;

import lombok.Builder;

/**
 * [결제 준비 응답 DTO]
 * PG사와의 결제 준비(Ready) 통신 성공 후, 클라이언트에게 리다이렉트 정보를 전달함.
 * Java Record와 Lombok @Builder를 조합하여 불변성과 가독성을 모두 확보함.
 */
@Builder
public record ChargeReadyResponseDTO(
        /** 내부 시스템에서 생성한 결제 요청 고유 식별자 (UUID) */
        UUID chargeId, 

        /** 사용자가 선택한 결제 수단 (예: KAKAO_PAY, NAVER_PAY) */
        String payType, 

        /** * [핵심] 클라이언트를 PG사의 실제 결제 인증 화면으로 이동시킬 URL.
         * 모바일/PC 환경에 따라 PG사가 제공한 적절한 주소를 매핑함.
         */
        String nextRedirectUrl, 

        /** * PG사에서 발급한 해당 거래의 고유 번호 (TID).
         * 차후 결제 승인(Approve) 요청 시 필수 파라미터로 사용됨.
         */
        String providerTid 
) {
}