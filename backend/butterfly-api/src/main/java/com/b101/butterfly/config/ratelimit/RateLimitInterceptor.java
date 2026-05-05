package com.b101.butterfly.config.ratelimit;

import com.b101.butterfly.auth.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rate limit 체크 인터셉터. {@code /api/**} 경로 적용 (auth/sdk/콜백 제외 — Web config에서).
 *
 * <p>스코프 결정:
 * <ul>
 *   <li>{@link CustomUserDetails} principal이 있으면 {@code member:<id>}</li>
 *   <li>없으면 {@code ip:<clientIp>} (X-Forwarded-For 우선, 없으면 remoteAddr)</li>
 * </ul>
 *
 * <p>응답 헤더:
 * <ul>
 *   <li>모든 요청: {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining}</li>
 *   <li>429 응답: {@code Retry-After: <seconds>} 추가</li>
 * </ul>
 *
 * <p>{@link RateLimitProperties#enabled()}=false면 패스 (로컬/테스트 비활성화 용).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    static final String HEADER_LIMIT = "X-RateLimit-Limit";
    static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    private static final String XFF_HEADER = "X-Forwarded-For";
    private static final int TOO_MANY_REQUESTS = 429;

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!Boolean.TRUE.equals(properties.enabled())) {
            return true;
        }

        String scope = resolveScope(request);
        RateLimiter.Decision decision = rateLimiter.tryAcquire(scope);

        response.setHeader(HEADER_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(HEADER_REMAINING, String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            log.warn("rate limit exceeded scope={} method={} uri={} retryAfter={}s",
                    scope, request.getMethod(), request.getRequestURI(), decision.retryAfterSeconds());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
            response.sendError(TOO_MANY_REQUESTS, "Rate limit exceeded");
            return false;
        }
        return true;
    }

    /**
     * 스코프 식별 — 인증 사용자면 memberId, 아니면 client IP.
     *
     * <p>SecurityContext가 비어있거나 {@link CustomUserDetails}가 아니면 IP fallback.
     * X-Forwarded-For 첫 번째 값(가장 먼저 본 client)을 우선, 없으면 {@link HttpServletRequest#getRemoteAddr()}.
     */
    private String resolveScope(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
            return "member:" + user.getMemberId();
        }
        String xff = request.getHeader(XFF_HEADER);
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
            if (!first.isEmpty()) {
                return "ip:" + first;
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
