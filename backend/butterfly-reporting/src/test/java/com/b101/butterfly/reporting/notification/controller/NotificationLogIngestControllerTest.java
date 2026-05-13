package com.b101.butterfly.reporting.notification.controller;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.service.NotificationLogIngestResult;
import com.b101.butterfly.reporting.notification.service.NotificationLogIngestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationLogIngestController.class)
@ActiveProfiles("api")
@Import({InternalTokenGuard.class, NotificationLogIngestControllerTest.TestSecurity.class})
@TestPropertySource(properties = "butterfly.internal.token=test-internal-token")
class NotificationLogIngestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private NotificationLogIngestService ingestService;

    private static final String VALID_BODY_JSON_TEMPLATE = """
            {
              "messageKey": "%s",
              "projectId": 123,
              "campaignId": 4521,
              "campaignExecutionId": 88123,
              "messageTemplateId": 89,
              "userId": 78901,
              "deviceId": 23456,
              "channel": "PUSH",
              "status": "SENT",
              "sentAt": "2026-04-28T10:00:05"
            }
            """;

    @BeforeEach
    void stubService() {
        when(ingestService.record(any())).thenReturn(
                new NotificationLogIngestResult(stubLog(98234521L, LocalDateTime.of(2026, 4, 28, 10, 0, 5, 500_000_000)), false)
        );
    }

    @Test
    @DisplayName("올바른 토큰 + 신규 메시지 → 201 + logId 반환")
    void newMessageReturns201() throws Exception {
        mockMvc.perform(post("/internal/notification-logs")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY_JSON_TEMPLATE.formatted("kafka-msg-new")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.logId").value(98234521L));
    }

    @Test
    @DisplayName("dedup hit → 200 + 동일 logId")
    void dedupReturns200() throws Exception {
        when(ingestService.record(any())).thenReturn(
                new NotificationLogIngestResult(stubLog(11L, LocalDateTime.now()), true)
        );

        mockMvc.perform(post("/internal/notification-logs")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY_JSON_TEMPLATE.formatted("kafka-msg-dup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logId").value(11L));
    }

    @Test
    @DisplayName("토큰 헤더 누락 → 401")
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(post("/internal/notification-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY_JSON_TEMPLATE.formatted("kafka-msg-x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 토큰 → 401")
    void wrongTokenReturns401() throws Exception {
        mockMvc.perform(post("/internal/notification-logs")
                        .header("X-Internal-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY_JSON_TEMPLATE.formatted("kafka-msg-y")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("필수 필드 누락 → 400 (jakarta validation)")
    void missingRequiredFieldReturns400() throws Exception {
        String invalidBody = """
                { "messageKey": "no-required-fields" }
                """;
        mockMvc.perform(post("/internal/notification-logs")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    /**
     * 슬라이스 테스트는 JPA를 띄우지 않으므로 NotificationLog 엔티티의 private 생성자를 직접
     * 호출할 수 없다. 리플렉션으로 id/createdAt만 채운 stub을 만든다.
     */
    private NotificationLog stubLog(Long id, LocalDateTime createdAt) {
        try {
            NotificationLog log = NotificationLog.create()
                    .projectId(1L).campaignId(1L).channel(Channel.PUSH).status(NotificationStatus.SENT)
                    .messageKey("k").build();
            Map<String, Object> values = Map.of("id", id, "createdAt", createdAt);
            for (var entry : values.entrySet()) {
                Field f = NotificationLog.class.getDeclaredField(entry.getKey());
                f.setAccessible(true);
                f.set(log, entry.getValue());
            }
            return log;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * butterfly-reporting은 spring-security를 가져오므로 @WebMvcTest가 기본 보안 필터를
     * 적용해 모든 요청을 401로 막는다. 본 컨트롤러는 자체 토큰 검증으로 인증을 해결하므로
     * SecurityFilterChain을 모두 허용으로 풀어준다.
     */
    @TestConfiguration
    static class TestSecurity {
        @Bean
        org.springframework.security.web.SecurityFilterChain testSecurity(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http
        ) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
