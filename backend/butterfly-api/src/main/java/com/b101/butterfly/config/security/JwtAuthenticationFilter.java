package com.b101.butterfly.config.security;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.security.CustomUserDetails;
import com.b101.butterfly.auth.service.JwtTokenProvider;
import com.b101.butterfly.auth.service.RedisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ADR-0005: JWT 인증 필터.
 *
 * <p>{@code Authorization: Bearer <token>} 헤더 파싱 → {@link JwtTokenProvider} 서명/만료 검증 →
 * Redis 블랙리스트({@code auth:blacklist:{jti}}) 체크 → {@link CustomUserDetails} principal로
 * SecurityContext 설정. 검증 실패 시 인증 미설정 (다운스트림 SecurityFilterChain이 401 처리).
 *
 * <p>본 필터는 api 모듈에서 신설 — auth 모듈은 read-only 정책 유지.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisService redisService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            if (!jwtTokenProvider.isValid(token)) {
                chain.doFilter(req, res);
                return;
            }

            String jti = jwtTokenProvider.getJti(token);
            if (jti != null && redisService.hasKey(BLACKLIST_KEY_PREFIX + jti)) {
                log.debug("JWT 블랙리스트 토큰 — 인증 미설정. jti={}", jti);
                chain.doFilter(req, res);
                return;
            }

            Long memberId = jwtTokenProvider.getMemberId(token);
            Long projectId = jwtTokenProvider.getProjectId(token);
            RoleType role = jwtTokenProvider.getRole(token);

            CustomUserDetails principal = new CustomUserDetails(memberId, projectId, role);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            // 인증 미설정 — 다운스트림이 401 처리. 토큰은 일부만 로깅 (PII/secret 보호).
            String tokenPreview = token.length() > 8 ? token.substring(0, 8) + "..." : "***";
            log.debug("JWT 인증 실패 — 인증 미설정. token={} reason={}", tokenPreview, e.getMessage());
        }
        chain.doFilter(req, res);
    }
}
