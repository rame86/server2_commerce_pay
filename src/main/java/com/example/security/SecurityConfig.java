package com.example.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.security.filter.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	
	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	
	@Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        // 이걸 등록해야 로그에서 "임시 비밀번호"가 사라집니다.
        return username -> null; 
    }
	
	@Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF 및 세션 설정 (REST API 방식)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 2. 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 2서버의 화이트리스트 경로 설정
                .requestMatchers("/api/payment/public/**").permitAll() 
                .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
            )
            
            // 3. 필터 배치 (가장 중요!)
            // UsernamePasswordAuthenticationFilter 실행 전에 우리가 만든 jwtAuthenticationFilter를 먼저 실행해라!
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
