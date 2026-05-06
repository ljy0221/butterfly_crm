package com.b101.butterfly.config.ratelimit;

/**
 * Rate limit 체크 인터페이스. Redis 구현({@link RedisRateLimiter})이 기본이지만, 테스트에서는
 * mock으로 대체 가능하도록 추상화한다.
 */
public interface RateLimiter {

    /**
     * 한 요청을 카운터에 기록하고 임계값 초과 여부와 잔여 시간을 반환한다.
     *
     * @param scope     스코프 식별자 ({@code member:42} / {@code ip:1.2.3.4} 등)
     * @param limit     이 호출에 적용할 RPM 임계값
     * @return {@link Decision} — allowed/limit/remaining/retryAfterSeconds 정보
     */
    Decision tryAcquire(String scope, int limit);

    /**
     * {@link #tryAcquire(String, int)} 의 기본 오버로드 — 구현체의 기본 burstCapacity 대신
     * 서브클래스가 직접 구현한 limit을 위임해야 하므로 이 default 메서드는 사용을 권장하지 않는다.
     * 하위 호환성을 위해 유지한다.
     *
     * @param scope 스코프 식별자
     * @return {@link Decision}
     * @deprecated {@link #tryAcquire(String, int)} 사용 권장
     */
    @Deprecated
    default Decision tryAcquire(String scope) {
        throw new UnsupportedOperationException("tryAcquire(String, int) 를 사용하세요.");
    }

    /**
     * Rate limit 체크 결과.
     *
     * @param allowed            true → 요청 허용, false → 429
     * @param limit              현재 윈도우 임계값 (응답 헤더 X-RateLimit-Limit 용)
     * @param remaining          남은 요청 수 (X-RateLimit-Remaining)
     * @param retryAfterSeconds  windows reset까지 남은 초 (Retry-After 헤더, allowed=false일 때 의미)
     */
    record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
    }
}
