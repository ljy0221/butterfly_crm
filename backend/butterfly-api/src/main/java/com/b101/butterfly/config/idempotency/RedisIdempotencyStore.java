package com.b101.butterfly.config.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link IdempotencyStore}의 Redis 구현체.
 *
 * <p>SETNX (setIfAbsent + ttl)로 atomic lock — value에 본문 SHA-256 fingerprint 저장.
 * 충돌 시 GET으로 비교해 같은 본문이면 단순 중복(409), 다른 본문이면 클라이언트 버그(422)로 분리.
 *
 * <p>Phase 4 응답 캐시는 별도 prefix({@link #RESPONSE_PREFIX})로 저장. body byte[]는 JSON
 * 직렬화 시 Jackson이 자동으로 Base64 처리한다 (record + ObjectMapper 기본 동작).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {

    static final String KEY_PREFIX = "idempotency:";
    static final String RESPONSE_PREFIX = "idempotency:response:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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

    @Override
    public void saveResponse(String key, CachedResponse response, Duration ttl) {
        String redisKey = RESPONSE_PREFIX + key;
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, json, ttl);
        } catch (JsonProcessingException e) {
            // 직렬화 실패는 캐시 저장 포기 — 다음 요청은 controller를 다시 거치게 됨 (안전 fallback).
            log.warn("Idempotency response cache serialize failed key={} reason={}", key, e.getMessage());
        }
    }

    @Override
    public Optional<CachedResponse> findResponse(String key) {
        String redisKey = RESPONSE_PREFIX + key;
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, CachedResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("Idempotency response cache deserialize failed key={} reason={}", key, e.getMessage());
            return Optional.empty();
        }
    }
}
