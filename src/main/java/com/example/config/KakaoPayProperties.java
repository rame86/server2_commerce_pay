// src/main/java/com/example/config/KakaoPayProperties.java
package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.pay")
public record KakaoPayProperties(
        String secretKey,
        String cid,
        String clientBaseUrl,
        String approvalUrl,
        String cancelUrl,
        String failUrl,
        String kakaopayBaseUrl) {
}