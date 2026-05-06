package com.b101.butterfly.config.ratelimit;

import java.util.Collections;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limiting 설정 — Redis 기반 분 단위 sliding-bucket 카운터.
 *
 * <p>스코프: 인증된 요청은 memberId, 비인증 요청은 client IP.
 * 임계값 초과 시 429 Too Many Requests + {@code Retry-After: <seconds>} 응답.
 *
 * <p>{@link #requestsPerMinute}는 1분당 정상 허용량, {@link #burstCapacity}는 초당 burst 흡수 상한.
 * 단순화된 분 단위 INCR 카운터에서는 burstCapacity가 임계값으로 사용된다 (분 단위 윈도우 내 누적
 * 요청이 burst 초과 시 거부).
 *
 * <p>{@link #endpointLimits}는 endpoint별 차등 RPM 설정.
 * key 포맷: HTTP_METHOD:URI_ANT_PATTERN (예: POST:/api/v1/projects/&#42;/campaigns).
 * 매칭되지 않는 endpoint는 {@link #burstCapacity}가 fallback으로 사용된다.
 */
@ConfigurationProperties(prefix = "butterfly.ratelimit")
public record RateLimitProperties(
        Boolean enabled,
        Integer requestsPerMinute,
        Integer burstCapacity,
        Map<String, Integer> endpointLimits
) {

    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_RPM = 60;
    private static final int DEFAULT_BURST = 100;

    public RateLimitProperties {
        if (enabled == null) {
            enabled = DEFAULT_ENABLED;
        }
        if (requestsPerMinute == null || requestsPerMinute <= 0) {
            requestsPerMinute = DEFAULT_RPM;
        }
        if (burstCapacity == null || burstCapacity <= 0) {
            burstCapacity = DEFAULT_BURST;
        }
        if (endpointLimits == null) {
            endpointLimits = Collections.emptyMap();
        }
    }
}
