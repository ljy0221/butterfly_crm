package com.b101.butterfly.reporting.notification.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka 컨슈머의 보조 멱등성 가드 (S14P31B101-180).
 *
 * <p>SETNX(=setIfAbsent) + TTL 7일로 {@code messageKey}를 트래킹한다. DB UNIQUE가
 * 최종 방어선이지만, Redis로 1차 차단하면 재시도 폭주 시 DB 부하/예외 비용을 줄일 수
 * 있다. Kafka retention(보통 7일)과 정합되도록 TTL을 7일로 둔다.
 *
 * <p>실패 정책 — Redis는 보조 방어선이므로:
 * <ul>
 *     <li>Redis 자체 장애 → 가드 통과시키고 DB UNIQUE에 위임 (가용성 우선)</li>
 *     <li>적재 후 예외 발생 → 키를 release하여 다음 재시도가 다시 진입할 수 있도록 한다</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class RedisMessageKeyGuard {

    static final String KEY_PREFIX = "notification-log:msg-key:";
    static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    /**
     * messageKey 점유 시도. 처음 보는 키면 {@code true}, 이미 처리한 키면 {@code false}.
     * Redis 장애 시 보수적으로 {@code true}를 반환해 DB UNIQUE에 위임한다.
     */
    public boolean tryAcquire(String messageKey) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + messageKey, "1", TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException e) {
            log.warn("Redis guard fail-open messageKey={} reason={}", messageKey, e.getMessage());
            return true;
        }
    }

    /** 적재 도중 예외 발생 시 키를 풀어 재시도/DLQ 흐름이 동일 키를 다시 처리할 수 있게 한다. */
    public void release(String messageKey) {
        try {
            redisTemplate.delete(KEY_PREFIX + messageKey);
        } catch (RuntimeException e) {
            log.warn("Redis guard release failed messageKey={} reason={}", messageKey, e.getMessage());
        }
    }
}
