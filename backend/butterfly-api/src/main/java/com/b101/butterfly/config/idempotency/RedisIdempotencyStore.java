package com.b101.butterfly.config.idempotency;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link IdempotencyStore}의 Redis 구현체.
 *
 * <p>SETNX (setIfAbsent + ttl)로 atomic lock — value에 본문 SHA-256 fingerprint 저장.
 * 충돌 시 GET으로 비교해 같은 본문이면 단순 중복(409), 다른 본문이면 클라이언트 버그(422)로 분리.
 */
@Component
@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {

    static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public LockOutcome tryLockWithFingerprint(String key, String fingerprint, Duration ttl) {
        String redisKey = KEY_PREFIX + key;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, fingerprint, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            return LockOutcome.ACQUIRED;
        }
        // 이미 점유된 키 — 본문 fingerprint 비교
        String existing = redisTemplate.opsForValue().get(redisKey);
        if (existing != null && existing.equals(fingerprint)) {
            return LockOutcome.FINGERPRINT_MATCH;
        }
        return LockOutcome.FINGERPRINT_MISMATCH;
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + key));
    }

    @Override
    public void release(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}
