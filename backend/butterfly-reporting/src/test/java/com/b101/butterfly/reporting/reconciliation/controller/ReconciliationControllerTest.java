package com.b101.butterfly.reporting.reconciliation.controller;

import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationItem;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationQuery;
import com.b101.butterfly.reporting.reconciliation.service.ReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S14P31B101-126 ReconciliationController MockMvc 슬라이스 테스트.
 *
 * <p>ADMIN 외 접근은 401/403, ADMIN은 정상 응답을 검증한다. 서비스 레이어는 mock으로 stub하여
 * 컨트롤러 + 보안 + 직렬화 경로만 검증한다.
 */
@WebMvcTest(controllers = ReconciliationController.class)
@Import(ReconciliationControllerTest.MethodSecurityEnabler.class)
class ReconciliationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ReconciliationService reconciliationService;

    @EnableMethodSecurity
    static class MethodSecurityEnabler {
    }

    private PageResponse<ReconciliationItem> samplePage() {
        ReconciliationItem item = new ReconciliationItem(
                1L, 123L, 4521L, 99L, Channel.PUSH,
                100, 95, 90, 5, 5, false, "5건 누락 감지",
                LocalDateTime.of(2026, 4, 28, 12, 0)
        );
        return PageResponse.of(List.of(item), 0, 20, 1L);
    }

    @Test
    @DisplayName("미인증 사용자는 GET /api/v1/reconciliations에 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("ADMIN 외 권한은 403")
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliations"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN — GET /api/v1/reconciliations 응답 200 + 페이지 항목")
    void adminListSuccess() throws Exception {
        when(reconciliationService.getList(any(ReconciliationQuery.class), anyInt(), anyInt()))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/reconciliations")
                        .param("projectId", "123")
                        .param("isConsistent", "false")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].reconId").value(1))
                .andExpect(jsonPath("$.data.content[0].channel").value("PUSH"))
                .andExpect(jsonPath("$.data.content[0].consistent").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN — GET /api/v1/reconciliations/inconsistent 호출 시 service.getInconsistent에 전달")
    void adminInconsistentDelegates() throws Exception {
        when(reconciliationService.getInconsistent(
                anyLong(), anyLong(), anyLong(), any(), any(), anyInt(), anyInt()))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/reconciliations/inconsistent")
                        .param("projectId", "123")
                        .param("campaignId", "4521")
                        .param("executionId", "99")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].consistent").value(false));

        verify(reconciliationService).getInconsistent(
                eq(123L), eq(4521L), eq(99L), any(), any(), eq(0), eq(20));
    }
}
