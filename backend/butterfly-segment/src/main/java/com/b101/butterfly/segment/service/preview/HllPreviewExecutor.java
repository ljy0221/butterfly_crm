package com.b101.butterfly.segment.service.preview;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
    /** S14P31B101-275 — ClickHouse/SQL count 흐름의 exact count 캐시 키. HLL 키와 별도. */
    private static final String EXACT_KEY_PREFIX = "segment:cnt:";

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
        return getCached(hash).map(c -> OptionalLong.of(c.count())).orElseGet(OptionalLong::empty);
    }

    /**
     * 캐시 조회 — count 와 method 같이 반환. S14P31B101-275 에서 추가.
     * 우선순위: exact count (SQL count, EXACT) > empty sentinel (EXACT) > HLL approximate (HLL).
     */
    public Optional<CachedCount> getCached(String hash) {
        String exact = redisTemplate.opsForValue().get(exactKey(hash));
        if (exact != null) {
            try {
                return Optional.of(new CachedCount(Long.parseLong(exact), "EXACT"));
            } catch (NumberFormatException ignored) {
                // 손상된 값 — miss 로 처리해 재계산
            }
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(emptyKey(hash)))) {
            return Optional.of(new CachedCount(0L, "EXACT"));
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(hllKey(hash)))) {
            return Optional.of(
                    new CachedCount(redisTemplate.opsForHyperLogLog().size(hllKey(hash)), "HLL"));
        }
        return Optional.empty();
    }

    public record CachedCount(long count, String method) {}

    /**
     * SQL count (정확값) 경로 전용 캐시 조회. HLL 잔재 캐시는 무시한다 (S14P31B101-275).
     *
     * <p>이전 빌드에서 SQL path 트리에 대해 HLL 캐시가 저장됐던 잔재가 있어도 새 경로는 항상
     * 정확값을 재계산하고 exact 키에 저장하도록 분리.
     */
    public OptionalLong getCachedExact(String hash) {
        String exact = redisTemplate.opsForValue().get(exactKey(hash));
        if (exact != null) {
            try {
                return OptionalLong.of(Long.parseLong(exact));
            } catch (NumberFormatException ignored) {
                // 손상된 값 — miss 로 처리해 재계산
            }
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(emptyKey(hash)))) {
            return OptionalLong.of(0L);
        }
        return OptionalLong.empty();
    }

    /**
     * SQL count 흐름의 정확값 캐시 (S14P31B101-275). 0 이면 empty sentinel, 그 외엔 exact key 에 저장.
     */
    public long cacheExactCount(String hash, long count, Duration ttl) {
        if (count == 0L) {
            redisTemplate.opsForValue().set(emptyKey(hash), "1", ttl);
        } else {
            redisTemplate.opsForValue().set(exactKey(hash), Long.toString(count), ttl);
        }
        return count;
    }

    private static String hllKey(String hash) {
        return HLL_KEY_PREFIX + hash;
    }

    private static String emptyKey(String hash) {
        return EMPTY_KEY_PREFIX + hash;
    }

    private static String exactKey(String hash) {
        return EXACT_KEY_PREFIX + hash;
    }
}
