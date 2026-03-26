// src/main/java/com/example/config/KakaoPayProperties.java
package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * [카카오페이 설정 정보 프로퍼티]
 * application.yml에 정의된 'kakao.pay' 하위 설정값들을 자동으로 바인딩함.
 * Record 형식을 사용하여 데이터 변경을 방지하고 코드 간결성을 확보함.
 */
@ConfigurationProperties(prefix = "kakao.pay")
public record KakaoPayProperties(
        /** 카카오페이 API 권한 인증용 비밀키 (Secret Key) */
        String secretKey,

        /** 가맹점 식별 코드 (테스트 시 일반적으로 "TC0ONETIME" 사용) */
        String cid,

        /** 프론트엔드 서비스의 기본 도메인 주소 */
        String clientBaseUrl,

        /** 결제 인증 성공 시 PG사로부터 요청을 받을 내부 API 주소 */
        String approvalUrl,

        /** 사용자가 결제창에서 취소 버튼을 눌렀을 때 이동할 주소 */
        String cancelUrl,

        /** 결제 처리 중 오류가 발생했을 때 이동할 주소 */
        String failUrl,

        /** 카카오페이 외부 API 서버의 기본 Endpoint 주소 */
        String kakaoPayBaseUrl) {
}