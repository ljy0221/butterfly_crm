package com.b101.butterfly.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RateLimitProperties")
class RateLimitPropertiesTest {

    @Test
    @DisplayName("null 입력 시 기본값 적용 — enabled=true / rpm=60 / burst=100")
    void defaultsApplied() {
        RateLimitProperties props = new RateLimitProperties(null, null, null);

        assertThat(props.enabled()).isTrue();
        assertThat(props.requestsPerMinute()).isEqualTo(60);
        assertThat(props.burstCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("0 또는 음수 입력 시 기본값으로 fallback")
    void invalidNumbersFallback() {
        RateLimitProperties props = new RateLimitProperties(true, 0, -5);

        assertThat(props.requestsPerMinute()).isEqualTo(60);
        assertThat(props.burstCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("정상 값은 그대로 유지")
    void valuesPreserved() {
        RateLimitProperties props = new RateLimitProperties(false, 30, 50);

        assertThat(props.enabled()).isFalse();
        assertThat(props.requestsPerMinute()).isEqualTo(30);
        assertThat(props.burstCapacity()).isEqualTo(50);
    }
}
