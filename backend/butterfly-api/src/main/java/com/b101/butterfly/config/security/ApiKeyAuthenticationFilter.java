package com.b101.butterfly.config.security;

import com.b101.butterfly.config.ratelimit.RateLimiter;
import com.b101.butterfly.global.security.ApiKeyValidator;
import com.b101.butterfly.global.util.HashUtils;
import com.b101.butterfly.tenant.adapter.ApiKeyValidatorImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * SDK Public API Key 인증 + 레이트 리밋 필터.
 *
 * <p>처리 순서:
 * <ol>
 *   <li>{@code X-API-Key} 헤더 존재 확인</li>
 *   <li>포맷 검증 (pk_live_ 접두사, 32자) — 불일치 시 DB 조회 없이 401</li>
 *   <li>SHA-256 해싱 후 {@link ApiKeyValidator#resolveProjectId} 조회 — 없으면 401</li>
 *   <li>{@link RateLimiter}로 프로젝트별 분 단위 한도 체크 — 초과 시 429</li>
 *   <li>request attribute {@code resolvedProjectId} 설정 후 다음 필터로 전달</li>
 * </ol>
 *
 * <p>SecurityConfig 등록은 Spring Security 활성화 시점에 수행한다 (S14P31B101-193).
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String RESOLVED_PROJECT_ID = "resolvedProjectId";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String RATE_LIMIT_SCOPE_PREFIX = "apikey:";
    private static final int TOO_MANY_REQUESTS = 429;

    private final ApiKeyValidator apiKeyValidator;
    private final RateLimiter rateLimiter;

    @Value("${butterfly.ratelimit.api-key.requests-per-second:100}")
    private int requestsPerSecond;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String apiKey = req.getHeader(API_KEY_HEADER);

        if (apiKey == null) {
            rejectWith(res, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_401", "X-API-Key 헤더가 없습니다");
            return;
        }

        if (!ApiKeyValidatorImpl.isValidFormat(apiKey)) {
            rejectWith(res, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_401", "API Key 형식이 올바르지 않습니다");
            return;
        }

        Optional<Long> projectId = apiKeyValidator.resolveProjectId(HashUtils.sha256(apiKey));

        if (projectId.isEmpty()) {
            rejectWith(res, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_401", "유효하지 않은 API Key입니다");
            return;
        }

        // 프로젝트별 레이트 리밋 — scope: apikey:{projectId}, limit: req/sec * 60 (분 단위 환산)
        String scope = RATE_LIMIT_SCOPE_PREFIX + projectId.get();
        RateLimiter.Decision decision = rateLimiter.tryAcquire(scope, requestsPerSecond * 60);

        if (!decision.allowed()) {
            log.debug("레이트 리밋 초과. projectId={}", projectId.get());
            res.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            rejectWith(res, TOO_MANY_REQUESTS, "RATE_429", "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요");
            return;
        }

        req.setAttribute(RESOLVED_PROJECT_ID, projectId.get());
        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/events");
    }

    private void rejectWith(HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(
                "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}"
        );
    }
}
