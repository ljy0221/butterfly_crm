package com.b101.butterfly.config.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Idempotency-Key 요청 본문을 두 번 읽기 위해 {@link ContentCachingRequestWrapper}로 감싸는 필터.
 *
 * <p>{@link IdempotencyKeyInterceptor}가 본문 SHA-256 fingerprint를 계산하려면 stream을
 * 한 번 더 읽어야 하지만, {@code @RequestBody}와 stream 충돌이 발생한다. 이 필터로 본문을
 * 메모리에 캐싱한 뒤 인터셉터/컨트롤러가 모두 같은 바이트를 본다.
 *
 * <p>Idempotency-Key 헤더가 없으면 wrapping을 건너뛰어 메모리 비용을 피한다 (대다수 요청).
 *
 * <p>적용 범위는 {@code /api/**} (콜백/sdk/swagger 제외) — {@link IdempotencyWebConfig}와 동일.
 *
 * <p>Order: {@link Ordered#HIGHEST_PRECEDENCE} + 10 — RequestIdFilter 다음, 다른 필터 이전.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyBodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (shouldWrap(request)) {
            chain.doFilter(new ContentCachingRequestWrapper(request), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean shouldWrap(HttpServletRequest request) {
        String key = request.getHeader(IdempotencyKeyInterceptor.HEADER);
        if (key == null || key.isBlank()) {
            return false;
        }
        return isMutating(request.getMethod());
    }

    private static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }
}
