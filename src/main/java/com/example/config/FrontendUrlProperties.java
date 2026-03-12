// src/main/java/com/example/config/FrontendUrlProperties.java
package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.url")
public record FrontendUrlProperties(
        String wallet,
        String success,
        String fail,
        String cancel
) {
}