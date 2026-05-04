package com.b101.butterfly.auth.service;

import com.b101.butterfly.auth.config.JwtProperties;
import com.b101.butterfly.auth.constant.RoleType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public String createAccessToken(Long memberId, Long projectId, RoleType role) {
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("projectId", projectId)
                .claim("role", role.name())
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessTokenExpiryMs()))
                .signWith(secretKey())
                .compact();
    }

    public String createRefreshToken(Long memberId) {
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshTokenExpiryMs()))
                .signWith(secretKey())
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getMemberId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public Long getProjectId(String token) {
        return parseClaims(token).get("projectId", Long.class);
    }

    public RoleType getRole(String token) {
        return RoleType.valueOf(parseClaims(token).get("role", String.class));
    }

    public String getJti(String token) {
        return parseClaims(token).get("jti", String.class);
    }

    public Duration getRemainingTtl(String token) {
        long remainingMs = parseClaims(token).getExpiration().getTime() - System.currentTimeMillis();
        return remainingMs > 0 ? Duration.ofMillis(remainingMs) : Duration.ZERO;
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
    }
}
