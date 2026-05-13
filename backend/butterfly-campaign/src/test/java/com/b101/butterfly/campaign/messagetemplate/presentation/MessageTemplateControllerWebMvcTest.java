package com.b101.butterfly.campaign.messagetemplate.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b101.butterfly.campaign.messagetemplate.application.MessageTemplateService;
import com.b101.butterfly.global.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MessageTemplateController WebMvcTest.
 *
 * <p>핵심 회귀 보호:
 * <ul>
 *   <li>본문 Bean Validation — 누락/blank/길이 초과 → 400</li>
 *   <li>PathVariable @Positive — 음수/0 → 400</li>
 * </ul>
 */
@WebMvcTest(MessageTemplateController.class)
@ActiveProfiles("api")
@Import(GlobalExceptionHandler.class)
@DisplayName("MessageTemplateController WebMvcTest")
class MessageTemplateControllerWebMvcTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long TEMPLATE_ID = 100L;
    private static final Long MEMBER_ID = 1L;
    private static final String BASE_URL = "/api/v1/projects/" + PROJECT_ID + "/templates";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MessageTemplateService messageTemplateService;

    @BeforeEach
    void setUpSecurityContext() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                String.valueOf(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_EDITOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("POST /templates — 템플릿 생성 본문 검증")
    class CreateValidation {

        @Test
        @DisplayName("정상 요청 → 201")
        void createHappy() throws Exception {
            given(messageTemplateService.createMessageTemplate(any())).willReturn(TEMPLATE_ID);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "messageTemplateName": "이름",
                                        "messageTemplateDescription": "설명",
                                        "channel": "PUSH",
                                        "messageTemplateTitle": "제목",
                                        "messageTemplateBody": "본문 #{name}"
                                    }
                                    """))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("messageTemplateName 누락 → 400")
        void createNameMissing() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "messageTemplateDescription": "설명",
                                        "channel": "PUSH",
                                        "messageTemplateTitle": "제목",
                                        "messageTemplateBody": "본문"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("messageTemplateBody blank → 400")
        void createBodyBlank() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "messageTemplateName": "이름",
                                        "messageTemplateDescription": "설명",
                                        "channel": "PUSH",
                                        "messageTemplateTitle": "제목",
                                        "messageTemplateBody": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("channel blank → 400")
        void createChannelBlank() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "messageTemplateName": "이름",
                                        "messageTemplateDescription": "설명",
                                        "channel": "",
                                        "messageTemplateTitle": "제목",
                                        "messageTemplateBody": "본문"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PathVariable @Positive 검증")
    class PathVariableValidation {

        @Test
        @DisplayName("DELETE /templates/0 → 400 (0 거부)")
        void deleteTemplateIdZero() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /templates/-5 → 400 (음수 거부)")
        void deleteTemplateIdNegative() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/-5"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /templates/{positive} → 200 + ApiResponse.ok()")
        void deleteTemplateIdHappy() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/" + TEMPLATE_ID))
                    .andExpect(status().isOk());

            verify(messageTemplateService).deleteMessageTemplate(TEMPLATE_ID, PROJECT_ID);
        }
    }

    @Nested
    @DisplayName("PUT /templates/{id} — 템플릿 수정 본문 검증")
    class UpdateValidation {

        @Test
        @DisplayName("정상 요청 → 200")
        void updateHappy() throws Exception {
            given(messageTemplateService.updateMessageTemplate(any())).willReturn(TEMPLATE_ID);

            mockMvc.perform(put(BASE_URL + "/" + TEMPLATE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "messageTemplateName": "이름",
                                        "messageTemplateDescription": "설명",
                                        "channel": "EMAIL",
                                        "messageTemplateTitle": "제목",
                                        "messageTemplateBody": "본문"
                                    }
                                    """))
                    .andExpect(status().isOk());
        }
    }
}
