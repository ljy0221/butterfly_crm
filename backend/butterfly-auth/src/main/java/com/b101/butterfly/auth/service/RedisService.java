package com.b101.butterfly.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    public void set(@NonNull String key, @NonNull String value, @NonNull Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public Optional<String> get(@NonNull String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void delete(@NonNull String key) {
        redisTemplate.delete(key);
    }

    public boolean hasKey(@NonNull String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /** 원자적 GET+DELETE. RTR 동시성 제어에 사용 (Redis GETDEL). */
    public Optional<String> getAndDelete(@NonNull String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key));
    }
}
