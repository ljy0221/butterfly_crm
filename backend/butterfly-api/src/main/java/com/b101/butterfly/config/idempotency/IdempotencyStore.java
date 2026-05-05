package com.b101.butterfly.config.idempotency;

import java.time.Duration;

/**
 * Idempotency-Key 저장소 추상.
 *
 * <p>Stripe 패턴 Phase 1: SETNX 기반 lock — 같은 키의 동시/재시도 요청을 1건만 통과시킨다.
 * Phase 2 (응답 캐시) / Phase 3 (body SHA-256) 확장 여지를 위해 인터페이스로 분리.
 */
public interface IdempotencyStore {

    /**
     * 키를 lock으로 점유한다 (SETNX). 이미 존재하면 false 반환.
     *
     * @param key Idempotency-Key 값 (멀티테넌트 prefix 포함, 호출자 책임)
     * @param ttl lock 유지 시간 (보통 24h)
     * @return 점유 성공 시 true, 이미 점유된 경우 false
     */
    boolean tryLock(String key, Duration ttl);

    /**
     * lock 점유 여부 조회 (조회 전용; 멱등 보장 외 검증용).
     */
    boolean exists(String key);

    /**
     * lock을 즉시 해제한다. 5xx 서버 에러 시 클라이언트 재시도를 허용하기 위해 사용.
     *
     * @param key Idempotency-Key 값
     */
    void release(String key);
}
