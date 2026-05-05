package com.b101.butterfly.config.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Idempotency-Key 헤더 처리 인터셉터 (Stripe 패턴 Phase 3: body fingerprint).
 *
 * <p>POST/PUT/PATCH/DELETE 요청에 {@code Idempotency-Key} 헤더가 있으면 본문 SHA-256
 * fingerprint를 계산해 SETNX로 24h lock value에 저장한다.
 *
 * <p>같은 키 충돌 처리:
 * <ul>
 *   <li>같은 본문 (fingerprint match) → 409 Conflict — 단순 재시도/중복</li>
 *   <li>다른 본문 (fingerprint mismatch) → 422 Unprocessable Entity — 클라이언트 버그
 *     (같은 키 재사용은 같은 요청 의도여야 함; 본문이 다르면 키 재사용 오류)</li>
 * </ul>
 *
 * <p>응답 상태가 5xx (서버 에러)이면 {@code afterCompletion}에서 lock을 즉시 해제해
 * 클라이언트가 재시도할 수 있도록 한다. 4xx는 lock 유지 — 클라이언트 에러는 재시도해도 동일 결과.
 *
 * <p>본문 읽기는 {@link IdempotencyBodyCachingFilter}가 미리 {@link ContentCachingRequestWrapper}로
 * 감싸 stream 두 번 읽기를 안전하게 만든다. 헤더가 없으면 wrap도 안 되므로 cost-zero.
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
            case FINGERPRINT_MATCH -> {
                log.warn("Idempotency-Key duplicate: key={} method={} uri={}",
                        key, request.getMethod(), request.getRequestURI());
                response.sendError(HttpStatus.CONFLICT.value(),
                        "Idempotency-Key already in use");
                yield false;
            }
            case FINGERPRINT_MISMATCH -> {
                log.warn("Idempotency-Key body mismatch: key={} method={} uri={}",
                        key, request.getMethod(), request.getRequestURI());
                response.sendError(UNPROCESSABLE_ENTITY,
                        "Idempotency-Key reused with different body");
                yield false;
            }
        };
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
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            // 컨트롤러가 아직 본문을 읽지 않았으므로 한 번 강제 읽고 캐시에 채운다
            try (InputStream in = wrapper.getInputStream()) {
                in.readAllBytes();
            } catch (IOException e) {
                log.warn("idempotency body read failed; treating as empty", e);
                return new byte[0];
            }
            return wrapper.getContentAsByteArray();
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
