package com.b101.butterfly.config.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Idempotency-Key 헤더 처리 인터셉터 (Stripe 패턴 Phase 2: 5xx unlock).
 *
 * <p>POST/PUT/PATCH/DELETE 요청에 {@code Idempotency-Key} 헤더가 있으면 SETNX로 24h lock을
 * 점유한다. 같은 키의 동시/재시도 요청은 409 Conflict로 거부된다.
 *
 * <p>응답 상태가 5xx (서버 에러)이면 {@code afterCompletion}에서 lock을 즉시 해제해
 * 클라이언트가 재시도할 수 있도록 한다. 4xx는 lock 유지 — 클라이언트 에러는 재시도해도 동일 결과.
 *
 * <p>헤더가 없으면 통과 — 기존 동작 보존.
 *
 * <p>Phase 3 (body SHA-256 검증)은 후속 작업에서 확장.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyInterceptor.class);
    public static final String HEADER = "Idempotency-Key";
    private static final Duration LOCK_TTL = Duration.ofHours(24);
    private static final int MIN_KEY_LEN = 8;
    private static final int MAX_KEY_LEN = 255;

    private final IdempotencyStore store;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!isMutating(request.getMethod())) {
            return true;
        }
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            return true;
        }
        if (key.length() < MIN_KEY_LEN || key.length() > MAX_KEY_LEN) {
            response.sendError(HttpStatus.BAD_REQUEST.value(),
                    "Idempotency-Key length must be 8..255");
            return false;
        }

        boolean acquired = store.tryLock(key, LOCK_TTL);
        if (!acquired) {
            log.warn("Idempotency-Key conflict: key={} method={} uri={}",
                    key, request.getMethod(), request.getRequestURI());
            response.sendError(HttpStatus.CONFLICT.value(),
                    "Idempotency-Key already in use");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        if (!isMutating(request.getMethod())) {
            return;
        }
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            return;
        }
        if (response.getStatus() >= 500) {
            store.release(key);
            log.info("idempotency lock released on 5xx status={} key={}", response.getStatus(), key);
        }
    }

    private static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }
}
