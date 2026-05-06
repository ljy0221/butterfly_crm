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
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Idempotency-Key 요청/응답 본문을 캐시하는 필터.
 *
 * <p>{@link IdempotencyKeyInterceptor}가 본문 SHA-256 fingerprint 계산(Phase 3) 및 응답 캐시
 * 저장(Phase 4)을 위해 stream을 다시 읽어야 한다. 요청은 {@link CachedBodyHttpServletRequest}로
 * 감싸 stream 두 번 읽기를 안전하게 만들고, 응답은 {@link ContentCachingResponseWrapper}로 감싸
 * afterCompletion에서 body byte를 추출할 수 있게 한다.
 *
 * <p>Idempotency-Key 헤더가 없으면 양쪽 모두 wrap하지 않아 일반 트래픽에 비용 0.
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
            ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
            chain.doFilter(new CachedBodyHttpServletRequest(request), wrappedResponse);
            // 응답 buffer를 client로 flush — interceptor afterCompletion에서 body byte 추출 후 호출.
            wrappedResponse.copyBodyToResponse();
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
