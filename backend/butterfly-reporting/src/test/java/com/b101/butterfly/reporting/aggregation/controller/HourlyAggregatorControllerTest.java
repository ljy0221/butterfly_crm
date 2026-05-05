package com.b101.butterfly.reporting.aggregation.controller;

import com.b101.butterfly.reporting.aggregation.service.HourlyAggregator;
import com.b101.butterfly.reporting.notification.controller.InternalTokenGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S14P31B101-124 (124-A) HourlyAggregatorController MockMvc 슬라이스 테스트.
 */
@WebMvcTest(controllers = HourlyAggregatorController.class)
@Import({InternalTokenGuard.class, HourlyAggregatorControllerTest.TestSecurity.class})
@TestPropertySource(properties = "butterfly.internal.token=test-internal-token")
class HourlyAggregatorControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HourlyAggregator hourlyAggregator;

    private static final String BODY = """
            { "bucketHour": "2026-04-28T15:00:00", "force": false }
            """;

    @Test
    @DisplayName("올바른 토큰 + 정상 페이로드 → 200 + processedRows 반환")
    void validReturns200() throws Exception {
        when(hourlyAggregator.aggregate(any(), anyBoolean())).thenReturn(7);

        mockMvc.perform(post("/internal/stats/aggregate/hourly")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processedRows").value(7));
    }

    @Test
    @DisplayName("토큰 헤더 누락 → 401")
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(post("/internal/stats/aggregate/hourly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 토큰 → 401")
    void wrongTokenReturns401() throws Exception {
        mockMvc.perform(post("/internal/stats/aggregate/hourly")
                        .header("X-Internal-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("필수 필드(bucketHour) 누락 → 400")
    void missingBucketHourReturns400() throws Exception {
        mockMvc.perform(post("/internal/stats/aggregate/hourly")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"force\": false }"))
                .andExpect(status().isBadRequest());
    }

    /**
     * butterfly-reporting의 spring-security가 @WebMvcTest 기본 SecurityFilterChain을 적용해
     * 모든 요청을 401로 막을 수 있다. 본 컨트롤러는 자체 토큰 가드로 인증을 해결하므로 보안
     * 필터를 모두 허용으로 풀어 토큰 검증 결과만 노출되게 한다.
     */
    @TestConfiguration
    static class TestSecurity {
        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
