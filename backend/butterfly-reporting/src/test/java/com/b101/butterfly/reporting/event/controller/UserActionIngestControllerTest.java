package com.b101.butterfly.reporting.event.controller;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.error.GlobalExceptionHandler;
import com.b101.butterfly.reporting.event.entity.ActionType;
import com.b101.butterfly.reporting.event.entity.UserActionLog;
import com.b101.butterfly.reporting.event.service.UserActionIngestService;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S14P31B101-123 (123-A) UserActionIngestController MockMvc 슬라이스 테스트.
 *
 * <p>스프링 표준 예외 처리(MethodNotSupported 405 / Validation 400)와 도메인 BusinessException
 * 처리(GlobalExceptionHandler) 를 둘 다 검증해야 하는데, GlobalExceptionHandler 의
 * `@ExceptionHandler(Exception.class)` 가 표준 예외도 500으로 일괄 변환하므로 두 케이스를 별도
 * 슬라이스로 분리해 각각 의도된 동작만 노출되게 한다:
 *
 * <ul>
 *     <li>{@link StandardSpringErrors} — handler 미등록 → 405/400 표준 동작 검증</li>
 *     <li>{@link BusinessExceptions} — handler 등록 → BusinessException → 적절한 상태 코드 변환 검증</li>
 * </ul>
 */
class UserActionIngestControllerTest {

    private static final String VALID_BODY = """
            {
              "logId": 98234521,
              "userId": 78901,
              "deviceId": 23456,
              "actionType": "CLICK",
              "actionTarget": "https://shop.example.com/promo",
              "userAgent": "Mozilla/5.0 (iPhone)",
              "actionAt": "2026-04-28T10:02:32"
            }
            """;

    /**
     * butterfly-reporting의 spring-security가 @WebMvcTest 기본 SecurityFilterChain으로 모든
     * 요청을 401로 막을 수 있다. 본 컨트롤러는 123-A 단계에서는 인증을 임시 허용 상태로 두므로
     * 보안 필터를 모두 풀어 검증 결과만 노출되게 한다.
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

    @Nested
    @WebMvcTest(controllers = UserActionIngestController.class)
    @ActiveProfiles("api")
    @Import(TestSecurity.class)
    @DisplayName("스프링 표준 예외 (handler 미등록 슬라이스)")
    class StandardSpringErrors {

        @Autowired private MockMvc mockMvc;
        @MockitoBean private UserActionIngestService userActionIngestService;

        @Test
        @DisplayName("정상 요청 → 201 + actionId 반환")
        void validReturns201() throws Exception {
            when(userActionIngestService.record(any(), any())).thenReturn(stubLog(99L));

            mockMvc.perform(post("/api/v1/events/actions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.actionId").value(99L));
        }

        @Test
        @DisplayName("필수 필드(actionType) 누락 → 400 (jakarta validation, 표준 동작)")
        void missingRequiredFieldReturns400() throws Exception {
            String invalid = """
                    { "messageKey": "kafka-msg-parent", "actionAt": "2026-04-28T10:02:32" }
                    """;
            mockMvc.perform(post("/api/v1/events/actions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE 메서드 → 405 Method Not Allowed")
        void deleteIsNotAllowed() throws Exception {
            mockMvc.perform(delete("/api/v1/events/actions"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("PUT 메서드 → 405 Method Not Allowed")
        void putIsNotAllowed() throws Exception {
            mockMvc.perform(put("/api/v1/events/actions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @WebMvcTest(controllers = UserActionIngestController.class)
    @ActiveProfiles("api")
    @Import({TestSecurity.class, GlobalExceptionHandler.class})
    @DisplayName("도메인 BusinessException (GlobalExceptionHandler 슬라이스)")
    class BusinessExceptions {

        @Autowired private MockMvc mockMvc;
        @MockitoBean private UserActionIngestService userActionIngestService;

        @Test
        @DisplayName("logId 미존재 → 400 LOG_NOT_FOUND (BusinessException → ANALYTICS_005)")
        void parentNotFoundReturns400() throws Exception {
            when(userActionIngestService.record(any(), any()))
                    .thenThrow(new BusinessException(AnalyticsErrorCode.LOG_NOT_FOUND));

            mockMvc.perform(post("/api/v1/events/actions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("ANALYTICS_005"));
        }

        @Test
        @DisplayName("CLICK + 잘못된 URL → 400 INVALID_ACTION_TARGET")
        void invalidActionTargetReturns400() throws Exception {
            when(userActionIngestService.record(any(), any()))
                    .thenThrow(new BusinessException(AnalyticsErrorCode.INVALID_ACTION_TARGET));

            mockMvc.perform(post("/api/v1/events/actions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("ANALYTICS_006"));
        }
    }

    /**
     * 슬라이스 테스트는 JPA를 띄우지 않으므로 UserActionLog의 private 생성자를 직접 호출할 수 없다.
     * 리플렉션으로 id/createdAt만 채운 stub을 만든다.
     */
    private static UserActionLog stubLog(Long id) {
        try {
            UserActionLog log = UserActionLog.create()
                    .logId(1L)
                    .projectId(1L)
                    .campaignId(1L)
                    .actionType(ActionType.CLICK)
                    .actionAt(LocalDateTime.now())
                    .build();
            Map<String, Object> values = Map.of("id", id, "createdAt", LocalDateTime.now());
            for (var entry : values.entrySet()) {
                Field f = UserActionLog.class.getDeclaredField(entry.getKey());
                f.setAccessible(true);
                f.set(log, entry.getValue());
            }
            return log;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
