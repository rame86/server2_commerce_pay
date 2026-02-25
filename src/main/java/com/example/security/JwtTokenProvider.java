package com.example.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {
	
	private final SecretKey key;
    private final long validityInMilliseconds = 3600000; // 1시간
    
    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
    	this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
    
    // 토큰 생성
    public String createToken(Long memberId, String role) {
    	// Claims생성(토큰에 담을 정보)
    	Claims claims = Jwts.claims().setSubject(String.valueOf(memberId));
    	claims.put("role", role);
    	
    	// 시간 생성
    	Date now = new Date();
    	Date validity = new Date(now.getTime() + validityInMilliseconds);
    	
    	return Jwts.builder()
    			.setClaims(claims)
    			.setIssuedAt(now)
    			.setExpiration(validity)
    			.signWith(key)
    			.compact();
    }
    
    // 토큰이 유효한지 검증
    public boolean validateToken(String token) {
    	try {
    		Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token);
        return true;
    	} catch (Exception e) {
    		return false;
    	}
    }
    
    // 토큰에서 유저ID를 꺼냄
    public String getSubject(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

}
