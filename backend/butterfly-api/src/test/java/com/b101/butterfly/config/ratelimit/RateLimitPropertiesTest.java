package com.b101.butterfly.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RateLimitProperties")
class RateLimitPropertiesTest {

    @Test
    @DisplayName("null 입력 시 기본값 적용 — enabled=true / rpm=60 / burst=100 / endpointLimits=empty")
    void defaultsApplied() {
        RateLimitProperties props = new RateLimitProperties(null, null, null, null);

        assertThat(props.enabled()).isTrue();
        assertThat(props.requestsPerMinute()).isEqualTo(60);
        assertThat(props.burstCapacity()).isEqualTo(100);
        assertThat(props.endpointLimits()).isEmpty();
    }

    @Test
    @DisplayName("0 또는 음수 입력 시 기본값으로 fallback")
    void invalidNumbersFallback() {
        RateLimitProperties props = new RateLimitProperties(true, 0, -5, null);

        assertThat(props.requestsPerMinute()).isEqualTo(60);
        assertThat(props.burstCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("정상 값은 그대로 유지")
    void valuesPreserved() {
        RateLimitProperties props = new RateLimitProperties(false, 30, 50, null);

        assertThat(props.enabled()).isFalse();
        assertThat(props.requestsPerMinute()).isEqualTo(30);
        assertThat(props.burstCapacity()).isEqualTo(50);
    }

    @Test
    @DisplayName("endpointLimits 파싱 — 제공된 Map이 그대로 유지됨")
    void endpointLimitsParsed() {
        Map<String, Integer> limits = Map.of(
                "POST:/api/v1/projects/*/campaigns", 30,
                "PATCH:/api/v1/projects/*/campaigns/*/status", 10,
                "GET:/api/v1/projects/*/campaigns/**", 200
        );
        RateLimitProperties props = new RateLimitProperties(true, 60, 100, limits);

        assertThat(props.endpointLimits()).containsEntry("POST:/api/v1/projects/*/campaigns", 30);
        assertThat(props.endpointLimits()).containsEntry("PATCH:/api/v1/projects/*/campaigns/*/status", 10);
        assertThat(props.endpointLimits()).containsEntry("GET:/api/v1/projects/*/campaigns/**", 200);
    }

    @Test
    @DisplayName("endpointLimits null → 빈 Map으로 초기화")
    void endpointLimitsNullInitializesToEmpty() {
        RateLimitProperties props = new RateLimitProperties(true, 60, 100, null);

        assertThat(props.endpointLimits()).isNotNull().isEmpty();
    }
}
