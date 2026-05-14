package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.response.UserProfileResponse;
import com.b101.butterfly.event.dto.response.UserProfileResponse.DeviceSummary;
import com.b101.butterfly.event.dto.response.UserProfileResponse.RecentEvent;
import com.b101.butterfly.event.dto.response.UserProfileResponse.Summary;
import com.b101.butterfly.event.exception.EventValidationExceptionHandler;
import com.b101.butterfly.event.service.UserProfileService;
import com.b101.butterfly.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("api")
@Import({GlobalExceptionHandler.class, EventValidationExceptionHandler.class})
class UserProfileControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserProfileService userProfileService;

    private static final Long PROJECT_ID = 1L;
    private static final Long USER_ID = 73L;
    private static final String URL = "/api/v1/projects/" + PROJECT_ID + "/users/" + USER_ID;

    @Test
    @DisplayName("GET /users/{userId} — 200 + 응답 필드 매핑 확인")
    void getProfileReturnsOk() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                73L,
                "user-73",
                true,
                LocalDateTime.of(2026, 4, 22, 10, 30),
                new DeviceSummary("IOS", "2.1.0", "17.0", true, LocalDateTime.now()),
                new Summary(47L, LocalDateTime.now(), 0L),
                List.of()
        );
        when(userProfileService.getProfile(PROJECT_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(73))
                .andExpect(jsonPath("$.data.externalId").value("user-73"))
                .andExpect(jsonPath("$.data.device.platform").value("IOS"))
                .andExpect(jsonPath("$.data.device.appVersion").value("2.1.0"))
                .andExpect(jsonPath("$.data.summary.totalEvents").value(47))
                .andExpect(jsonPath("$.data.summary.purchaseCount").value(0));
    }

    @Test
    @DisplayName("GET /users/{userId} — recent events 배열 매핑 확인")
    void getProfileReturnsRecentEventsArray() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                73L, "user-73", true,
                LocalDateTime.of(2026, 4, 22, 10, 30),
                null,
                new Summary(2L, LocalDateTime.of(2026, 5, 14, 15, 30), 0L),
                List.of(
                        new RecentEvent(1001L, "product_view",
                                LocalDateTime.of(2026, 5, 14, 15, 30), "{\"category\":\"운동화\"}"),
                        new RecentEvent(1002L, "page_view",
                                LocalDateTime.of(2026, 5, 14, 14, 0), "{}")
                )
        );
        when(userProfileService.getProfile(PROJECT_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentEvents.length()").value(2))
                .andExpect(jsonPath("$.data.recentEvents[0].eventCategory").value("product_view"))
                .andExpect(jsonPath("$.data.recentEvents[0].eventData.category").value("운동화"))
                .andExpect(jsonPath("$.data.device").doesNotExist());
    }
}
