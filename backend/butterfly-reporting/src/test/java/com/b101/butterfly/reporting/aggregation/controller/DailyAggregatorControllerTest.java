package com.b101.butterfly.reporting.aggregation.controller;

import com.b101.butterfly.reporting.aggregation.dto.DailyRollupResponse;
import com.b101.butterfly.reporting.aggregation.service.DailyAggregator;
import com.b101.butterfly.reporting.notification.controller.InternalTokenGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyAggregatorControllerTest {

    @TestConfiguration
    static class TestSecurity {
        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Nested
    @WebMvcTest(controllers = DailyAggregatorController.class)
    @Import(TestSecurity.class)
    @DisplayName("DailyAggregatorController 슬라이스 테스트")
    class Slice {

        @Autowired private MockMvc mockMvc;
        @MockitoBean private DailyAggregator dailyAggregator;
        @MockitoBean private InternalTokenGuard internalTokenGuard;

        @Test
        @DisplayName("정상 요청 → 200 + campaignRows 반환")
        void validRequestReturns200() throws Exception {
            when(dailyAggregator.rollup(any(), anyBoolean()))
                    .thenReturn(new DailyRollupResponse(3, 2, 1, 1, 120L));

            mockMvc.perform(post("/internal/stats/rollup/daily")
                            .header("X-Internal-Token", "test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"bucketDate": "2026-05-01", "force": false}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.campaignRows").value(3));
        }

        @Test
        @DisplayName("bucketDate 누락 → 400")
        void missingBucketDateReturns400() throws Exception {
            mockMvc.perform(post("/internal/stats/rollup/daily")
                            .header("X-Internal-Token", "test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"force": false}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }
}
