// src/main/java/com/example/payment/config/KakaoPayProperties.java
package com.example.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// record 타입
@ConfigurationProperties(prefix = "kakao.pay")
public record KakaoPayProperties(
        String secretKey,
        String cid,
        String approvalUrl,
        String cancelUrl,
        String failUrl,
        String baseUrl
) {
}