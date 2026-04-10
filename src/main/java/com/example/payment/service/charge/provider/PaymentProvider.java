// src/main/java/com/example/payment/service/charge/provider/PaymentProvider.java

package com.example.payment.service.charge.provider;

import com.example.payment.dto.user.ChargeReadyResponseDTO;
import com.example.payment.entity.Charge;

/**
 * [PG사별 결제 처리 인터페이스]
 * 전략 패턴(Strategy Pattern)을 적용하여 각 결제 수단(카카오페이, 네이버페이 등)의
 * 서로 다른 외부 API 호출 로직을 공통된 규격으로 정의
 * 해당 인터페이스를 상속받은 각 PG사별 구현체는 supports() 메서드로 자신이 처리할 수 있는 PG사 식별자를 정의하고,
 * ready()와 approve() 메서드에서 해당 PG사의 API 명세에 맞게 결제 준비 및 승인 로직을 구현함
 * OCP(개방-폐쇄 원칙) 준수: 새로운 결제 수단(예: 토스페이)이 추가되어도
 * 기존 ChargeServiceImpl 코드는 단 한 줄도 건드릴 필요가 없음
  */
public interface PaymentProvider {

    /**
     * [PG사 지원 여부 확인]
     * 전달된 pgProvider 식별자(예: "KAKAO_PAY", "NAVER_PAY")를 처리할 수 있는지 판단
     * ChargeServiceImpl 의.stream.filter()는 내부적으로 boolean(true/false) 반환을 요구
     * supports()가 true를 반환하는 프로바이더 객체만 필터링되어 살아남음
     * * @param pgProvider 결제 수단 식별 문자열
     * @return 지원 가능 여부 (true/false)*/
    boolean supports(String pgProvider);

    /**
     * [STEP 1: PG사 결제 준비 (Ready) API 호출]
     * PG사 측에 결제 정보를 전달하고, 결제 요청을 식별할 수 있는 외부 거래 번호(TID) 및 결제창 URL 등을 발급.
     * * @param charge 결제 금액 및 원장 정보가 담긴 엔티티
     * @param memberId 결제를 진행하는 사용자 ID
     * @param token    인증 및 보안을 위한 사용자 토큰
     * @return 결제 준비 결과 DTO (TID, 결제창 리다이렉트용 URL 등 포함)
     */
    ChargeReadyResponseDTO ready(Charge charge, Long memberId);

    /**
     * [STEP 2: PG사 결제 승인 (Approve) API 호출]
     * 사용자가 결제 인증(비밀번호 입력 등)을 완료한 후,PG사에 실제로 결제 확정을 요청하여 거래를 완료.
     * * @param charge 승인할 결제 원장 정보
     * @param pgToken PG사로부터 리다이렉트 시 전달받은 인증 토큰 (예: pg_token)
     */
    void approve(Charge charge, String pgToken);

}