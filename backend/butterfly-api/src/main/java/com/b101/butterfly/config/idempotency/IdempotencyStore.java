package com.b101.butterfly.config.idempotency;

import java.time.Duration;

/**
 * Idempotency-Key 저장소 추상.
 *
 * <p>Stripe 패턴 진화 단계:
 * <ul>
 *   <li>Phase 1: SETNX 기반 lock — 같은 키의 동시/재시도 요청을 1건만 통과</li>
 *   <li>Phase 2: 5xx 응답 시 lock 즉시 해제 — 재시도 허용</li>
 *   <li>Phase 3: 본문 SHA-256 fingerprint를 lock value로 함께 저장 — 같은 키로 다른 본문이
 *     재요청되면 422 Unprocessable Entity로 거부 (요청 중복 vs 클라이언트 버그 구분)</li>
 * </ul>
 */
public interface IdempotencyStore {

    /**
     * 키를 lock으로 점유하고, 본문 fingerprint를 함께 저장한다 (atomic SETNX).
     *
     * @param key         Idempotency-Key 헤더 값
     * @param fingerprint 요청 본문 SHA-256 hex (없으면 빈 본문 표시 토큰 — 호출자 책임)
     * @param ttl         lock 유지 시간 (보통 24h)
     * @return 점유 결과 {@link LockOutcome}: ACQUIRED / FINGERPRINT_MATCH / FINGERPRINT_MISMATCH
     */
    LockOutcome tryLockWithFingerprint(String key, String fingerprint, Duration ttl);

    /**
     * lock 점유 여부 조회 (조회 전용; 멱등 보장 외 검증용).
     */
    boolean exists(String key);

    /**
     * lock을 즉시 해제한다. 5xx 서버 에러 시 클라이언트 재시도를 허용하기 위해 사용.
     */
    void release(String key);

    /**
     * lock 점유 시도 결과.
     *
     * <p>{@link #ACQUIRED}: 새 lock 획득 — 요청 처리 진행.
     * <p>{@link #FINGERPRINT_MATCH}: 같은 키 + 같은 본문 — 중복 요청, 409 Conflict.
     * <p>{@link #FINGERPRINT_MISMATCH}: 같은 키 + 다른 본문 — 클라이언트 버그, 422 Unprocessable Entity.
     */
    enum LockOutcome {
        ACQUIRED,
        FINGERPRINT_MATCH,
        FINGERPRINT_MISMATCH
    }
}
