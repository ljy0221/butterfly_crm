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
        RateLimitProperties props = new RateLimitProperties(true, 60, 100, null);
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

    @Test
    @DisplayName("tryAcquire(scope, limit) — 지정한 limit 적용")
    void tryAcquireWithExplicitLimit() {
        given(valueOps.increment(anyString())).willReturn(5L);

        RateLimiter.Decision decision = limiter.tryAcquire("ip:1.2.3.4", 10);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(10);
        assertThat(decision.remaining()).isEqualTo(5);
    }

    @Test
    @DisplayName("tryAcquire(scope, limit) — limit 10, count=11 → denied")
    void tryAcquireWithExplicitLimitDenied() {
        given(valueOps.increment(anyString())).willReturn(11L);

        RateLimiter.Decision decision = limiter.tryAcquire("ip:1.2.3.4", 10);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.limit()).isEqualTo(10);
        assertThat(decision.remaining()).isEqualTo(0);
        assertThat(decision.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("tryAcquire(scope, limit) — limit 200, count=1 → remaining=199")
    void tryAcquireWithHighLimit() {
        given(valueOps.increment(anyString())).willReturn(1L);

        RateLimiter.Decision decision = limiter.tryAcquire("ip:1.2.3.4", 200);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(200);
        assertThat(decision.remaining()).isEqualTo(199);
        verify(redisTemplate, times(1)).expire(anyString(), any());
    }

    @Test
    @DisplayName("tryAcquire(scope) deprecated 오버로드 → properties.burstCapacity(100) 사용")
    @SuppressWarnings("deprecation")
    void legacyTryAcquireUsesBurstCapacity() {
        given(valueOps.increment(anyString())).willReturn(50L);

        RateLimiter.Decision decision = limiter.tryAcquire("ip:1.2.3.4");

        assertThat(decision.limit()).isEqualTo(100);
        assertThat(decision.remaining()).isEqualTo(50);
    }
}
