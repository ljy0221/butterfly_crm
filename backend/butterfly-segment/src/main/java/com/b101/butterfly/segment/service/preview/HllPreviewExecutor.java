package com.b101.butterfly.segment.service.preview;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

/**
 * Redis HyperLogLog 기반 미리보기 실행기. PFADD/PFCOUNT (`opsForHyperLogLog`) + 0명 sentinel.
 *
 * <p>키 정책 (ADR-0006 §5):
 * <ul>
 *   <li>{@code segment:hll:{hash}} — HyperLogLog 본체 (TTL 24h)
 *   <li>{@code segment:hll:empty:{hash}} — 0명 sentinel (TTL 24h, PFADD가 빈 set에는 키 생성 안 함)
 * </ul>
 *
 * <p>0명 결과와 캐시 miss를 구분하기 위해 sentinel 분리 — Codex 권고.
 */
@Component
public class HllPreviewExecutor {

    private static final String HLL_KEY_PREFIX = "segment:hll:";
    private static final String EMPTY_KEY_PREFIX = "segment:hll:empty:";

    private final StringRedisTemplate redisTemplate;

    public HllPreviewExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long computeAndCache(String hash, List<Long> userIds, Duration ttl) {
        if (userIds == null || userIds.isEmpty()) {
            redisTemplate.opsForValue().set(emptyKey(hash), "1", ttl);
            return 0L;
        }
        String hllKey = hllKey(hash);
        String[] values = userIds.stream().map(String::valueOf).toArray(String[]::new);
        redisTemplate.opsForHyperLogLog().add(hllKey, values);
        redisTemplate.expire(hllKey, ttl);
        return redisTemplate.opsForHyperLogLog().size(hllKey);
    }

    public OptionalLong getCachedCount(String hash) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(emptyKey(hash)))) {
            return OptionalLong.of(0L);
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(hllKey(hash)))) {
            return OptionalLong.of(redisTemplate.opsForHyperLogLog().size(hllKey(hash)));
        }
        return OptionalLong.empty();
    }

    private static String hllKey(String hash) {
        return HLL_KEY_PREFIX + hash;
    }

    private static String emptyKey(String hash) {
        return EMPTY_KEY_PREFIX + hash;
    }
}
