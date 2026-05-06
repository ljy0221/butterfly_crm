package com.b101.butterfly.config.idempotency;

import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Idempotency-Key 헤더 처리 인터셉터 (Stripe 패턴 Phase 4: 응답 캐시 완성).
 *
 * <p>POST/PUT/PATCH/DELETE 요청에 {@code Idempotency-Key} 헤더가 있으면 본문 SHA-256
 * fingerprint를 계산해 SETNX로 24h lock value에 저장한다.
 *
 * <p>같은 키 충돌 처리:
 * <ul>
 *   <li>같은 본문 (fingerprint match) + 캐시된 응답 있음 → controller 거치지 않고 캐시 응답 즉시 반환 (Phase 4)</li>
 *   <li>같은 본문 (fingerprint match) + 캐시 없음 (첫 요청 진행 중) → 409 Conflict</li>
 *   <li>다른 본문 (fingerprint mismatch) → 422 Unprocessable Entity — 클라이언트 버그
 *     (같은 키 재사용은 같은 요청 의도여야 함; 본문이 다르면 키 재사용 오류)</li>
 * </ul>
 *
 * <p>응답 처리 ({@code afterCompletion}):
 * <ul>
 *   <li>5xx (서버 에러): lock 즉시 해제 — 클라이언트 재시도 허용 (Phase 2)</li>
 *   <li>2xx/3xx/4xx: 응답 본문을 캐시 저장 (Phase 4) — 다음 동일 키 + 동일 본문 재요청에 즉답</li>
 * </ul>
 *
 * <p>본문 입출력은 {@link IdempotencyBodyCachingFilter}가 미리 {@link CachedBodyHttpServletRequest}/
 * {@link ContentCachingResponseWrapper}로 감싸 stream 두 번 읽기를 안전하게 만든다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyInterceptor.class);
    public static final String HEADER = "Idempotency-Key";
    private static final Duration LOCK_TTL = Duration.ofHours(24);
    private static final int MIN_KEY_LEN = 8;
    private static final int MAX_KEY_LEN = 255;
    private static final String EMPTY_BODY_FINGERPRINT = "EMPTY";
    private static final int UNPROCESSABLE_ENTITY = 422;

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

        String fingerprint = computeBodyFingerprint(request);
        IdempotencyStore.LockOutcome outcome =
                store.tryLockWithFingerprint(key, fingerprint, LOCK_TTL);

        return switch (outcome) {
            case ACQUIRED -> true;
            case FINGERPRINT_MATCH -> handleFingerprintMatch(key, fingerprint, request, response);
            case FINGERPRINT_MISMATCH -> {
                log.warn("Idempotency-Key body mismatch: key={} method={} uri={}",
                        key, request.getMethod(), request.getRequestURI());
                response.sendError(UNPROCESSABLE_ENTITY,
                        "Idempotency-Key reused with different body");
                yield false;
            }
        };
    }

    /**
     * 같은 키 + 같은 본문 충돌 처리. 캐시된 응답이 있으면 그대로 write하고 chain 중단.
     * 캐시 없으면 409 Conflict (첫 요청이 아직 진행 중이거나 5xx 후 재시도 케이스).
     */
    private boolean handleFingerprintMatch(String key,
                                           String fingerprint,
                                           HttpServletRequest request,
                                           HttpServletResponse response) throws IOException {
        Optional<CachedResponse> cached = store.findResponse(key);
        if (cached.isPresent() && fingerprint.equals(cached.get().requestBodyHash())) {
            CachedResponse hit = cached.get();
            log.info("Idempotency cache hit: key={} method={} uri={} status={}",
                    key, request.getMethod(), request.getRequestURI(), hit.status());
            writeCachedResponse(response, hit);
            return false;
        }
        log.warn("Idempotency-Key duplicate (no cache): key={} method={} uri={}",
                key, request.getMethod(), request.getRequestURI());
        response.sendError(HttpStatus.CONFLICT.value(),
                "Idempotency-Key already in use");
        return false;
    }

    private void writeCachedResponse(HttpServletResponse response, CachedResponse cached) throws IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) {
            response.setContentType(cached.contentType());
        }
        byte[] body = cached.body();
        if (body != null && body.length > 0) {
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            response.getOutputStream().flush();
        }
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
        int status = response.getStatus();
        if (status >= 500) {
            store.release(key);
            log.info("idempotency lock released on 5xx status={} key={}", status, key);
            return;
        }
        // 2xx/3xx/4xx — 응답 캐시 저장 (Phase 4)
        ContentCachingResponseWrapper wrapper = unwrap(response);
        if (wrapper == null) {
            return;
        }
        byte[] body = wrapper.getContentAsByteArray();
        String contentType = wrapper.getContentType();
        String fingerprint = computeBodyFingerprint(request);
        CachedResponse snapshot = new CachedResponse(status, contentType, body, fingerprint);
        store.saveResponse(key, snapshot, LOCK_TTL);
        log.debug("idempotency response cached key={} status={} bytes={}", key, status,
                body == null ? 0 : body.length);
    }

    private static ContentCachingResponseWrapper unwrap(HttpServletResponse response) {
        ServletResponse current = response;
        while (current instanceof HttpServletResponseWrapper wrapper) {
            if (wrapper instanceof ContentCachingResponseWrapper ccrw) {
                return ccrw;
            }
            current = wrapper.getResponse();
        }
        return null;
    }

    /**
     * 요청 본문 SHA-256 hex를 계산한다.
     *
     * <p>본문이 없거나 wrap되지 않은 요청(헤더 없음 등)은 {@link #EMPTY_BODY_FINGERPRINT}로 처리.
     * SHA-256 알고리즘이 JRE에 없으면 발생할 수 없는 환경 문제 — 빈 fingerprint로 fallback해
     * Phase 3 보호는 비활성화되지만 Phase 1/2 lock은 계속 동작한다.
     */
    private String computeBodyFingerprint(HttpServletRequest request) {
        byte[] body = readBody(request);
        if (body.length == 0) {
            return EMPTY_BODY_FINGERPRINT;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body);
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available; idempotency body verification disabled", e);
            return EMPTY_BODY_FINGERPRINT;
        }
    }

    private byte[] readBody(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequest wrapper) {
            return wrapper.getCachedBody();
        }
        // BodyCachingFilter가 wrap하지 않은 경우 (헤더 없음 — 도달 불가)
        return new byte[0];
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }
}
