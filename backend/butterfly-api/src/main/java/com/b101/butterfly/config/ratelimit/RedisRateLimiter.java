package com.b101.butterfly.config.ratelimit;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 분 단위 카운터 기반 {@link RateLimiter} 구현.
 *
 * <p>키 포맷: {@code ratelimit:<scope>:<minuteBucket>}
 * <ul>
 *   <li>INCR로 카운터 증가 → 첫 증가 시 EXPIRE 70초로 windowed reset</li>
 *   <li>burstCapacity(임계값) 초과 시 allowed=false + 다음 분까지 retry-after</li>
 * </ul>
 *
 * <p>분 단위 fixed window 한계: 윈도우 경계에서 잠깐 burst 2배까지 허용될 수 있음.
 * 본 구현은 단순성과 성능을 우선 — sliding-window precise 동작이 필요하면 Lua script로 교체 가능.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";
    private static final Duration WINDOW_TTL = Duration.ofSeconds(70);
    private static final int SECONDS_PER_MINUTE = 60;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    @Override
    public Decision tryAcquire(String scope, int limit) {
        long nowEpochSeconds = Instant.now().getEpochSecond();
        long minuteBucket = nowEpochSeconds / SECONDS_PER_MINUTE;
        String redisKey = KEY_PREFIX + scope + ":" + minuteBucket;

        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, WINDOW_TTL);
        }

        int current = count == null ? 1 : count.intValue();
        int remaining = Math.max(0, limit - current);
        long retryAfter = SECONDS_PER_MINUTE - (nowEpochSeconds % SECONDS_PER_MINUTE);

        if (current > limit) {
            return new Decision(false, limit, 0, retryAfter);
        }
        return new Decision(true, limit, remaining, 0L);
    }

    /**
     * 하위 호환 오버로드 — properties.burstCapacity()를 limit으로 사용.
     */
    @Override
    @Deprecated
    public Decision tryAcquire(String scope) {
        return tryAcquire(scope, properties.burstCapacity());
    }
}
