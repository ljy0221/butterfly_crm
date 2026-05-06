package com.b101.butterfly.config.idempotency;

/**
 * Idempotency-Key Phase 4 응답 캐시 저장 단위.
 *
 * <p>같은 키 + 같은 본문 fingerprint로 재요청이 들어오면 controller를 거치지 않고
 * 이 record의 {@link #status}/{@link #contentType}/{@link #body}를 그대로 응답한다.
 * {@link #requestBodyHash}는 lock value(SETNX) fingerprint와의 일치 여부를 한 번 더
 * 검증하기 위한 보조 필드 — Phase 3 mismatch 보호와 일관성을 유지한다.
 *
 * <p>byte[] body는 응답이 작은 경우(JSON ApiResponse 한 건)에 한해 적합. ApiResponse 페이로드는
 * 대체로 KB 단위이며 24h TTL과 함께 저장한다.
 */
public record CachedResponse(
        int status,
        String contentType,
        byte[] body,
        String requestBodyHash
) {
}
