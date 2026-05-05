package com.b101.butterfly.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@DisplayName("RedisRateLimiter")
class RedisRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        RateLimitProperties props = new RateLimitProperties(true, 60, 100);
        limiter = new RedisRateLimiter(redisTemplate, props);
    }

    @Test
    @DisplayName("첫 요청 → INCR=1 + EXPIRE 호출 + allowed=true")
    void firstRequestSetsExpiry() {
        given(valueOps.increment(anyString())).willReturn(1L);

        RateLimiter.Decision decision = limiter.tryAcquire("ip:1.2.3.4");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(100);
        assertThat(decision.remaining()).isEqualTo(99);
        verify(redisTemplate, times(1)).expire(anyString(), any());
    }

    @Test
    @DisplayName("두 번째 이후 요청 → EXPIRE 호출 안 함")
    void subsequentRequestNoExpiry() {
        given(valueOps.increment(anyString())).willReturn(2L);

        limiter.tryAcquire("ip:1.2.3.4");

        verify(redisTemplate, times(0)).expire(anyString(), any());
    }

    @Test
    @DisplayName("burst capacity와 같은 카운트 → 허용 (마지막 1개)")
    void atLimitAllowed() {
        given(valueOps.increment(anyString())).willReturn(100L);

        RateLimiter.Decision decision = limiter.tryAcquire("ip:1.2.3.4");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("burst capacity 초과 → allowed=false + retryAfter > 0")
    void overLimitDenied() {
        given(valueOps.increment(anyString())).willReturn(101L);

        RateLimiter.Decision decision = limiter.tryAcquire("ip:1.2.3.4");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isEqualTo(0);
        assertThat(decision.retryAfterSeconds()).isBetween(1L, 60L);
    }
}
