package com.b101.butterfly.config.idempotency;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link IdempotencyStore}의 Redis 구현체. SETNX (setIfAbsent + ttl)로 atomic lock.
 */
@Component
@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {

    static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryLock(String key, Duration ttl) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + key, "1", ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + key));
    }
}
