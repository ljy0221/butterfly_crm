package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.request.EventCollectRequest;
import com.b101.butterfly.event.dto.response.EventCollectResponse;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.exception.EventValidationExceptionHandler;
import com.b101.butterfly.event.service.EventCollectService;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventCollectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, EventValidationExceptionHandler.class})
class EventCollectControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EventCollectService eventCollectService;

    private static final String URL = "/api/v1/events";
    private static final Long PROJECT_ID = 100L;

    private String validBody() throws Exception {
        EventCollectRequest req = new EventCollectRequest(
                "u_kim_xyz",
                "dev_abc789",
                "page_view",
                LocalDateTime.of(2026, 4, 30, 12, 0),
                objectMapper.readTree("{\"page\":\"/home\"}")
        );
        return objectMapper.writeValueAsString(req);
    }

    @Test
    @DisplayName("POST /events — 정상 요청은 200 + ApiResponse 래핑된 EventCollectResponse")
    void collectReturnsOk() throws Exception {
        EventCollectResponse response = new EventCollectResponse(
                "uuid-123", LocalDateTime.of(2026, 4, 30, 12, 0, 1), 1
        );
        when(eventCollectService.collect(eq(PROJECT_ID), any(EventCollectRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value("uuid-123"))
                .andExpect(jsonPath("$.data.schemaVersion").value(1))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("POST /events — userId가 null이어도 200 (익명 허용)")
    void collectAllowsNullUserId() throws Exception {
        when(eventCollectService.collect(eq(PROJECT_ID), any(EventCollectRequest.class)))
                .thenReturn(new EventCollectResponse("uuid-anon", LocalDateTime.now(), 1));

        String anonBody = """
                {
                  "userId": null,
                  "deviceId": "dev_abc789",
                  "eventCategory": "page_view",
                  "occurredAt": "2026-04-30T12:00:00",
                  "eventData": null
                }
                """;

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /events — service가 CATEGORY_NOT_FOUND 던지면 404 + ApiResponse.fail")
    void collectMapsCategoryNotFoundTo404() throws Exception {
        when(eventCollectService.collect(eq(PROJECT_ID), any(EventCollectRequest.class)))
                .thenThrow(new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /events — service가 DEVICE_NOT_REGISTERED 던지면 404 + ApiResponse.fail")
    void collectMapsDeviceNotRegisteredTo404() throws Exception {
        when(eventCollectService.collect(eq(PROJECT_ID), any(EventCollectRequest.class)))
                .thenThrow(new BusinessException(EventErrorCode.DEVICE_NOT_REGISTERED));

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEVICE_NOT_REGISTERED"));
    }

    @Test
    @DisplayName("POST /events — deviceId 빈 문자열은 400 (validation 실패)")
    void collectRejectsBlankDeviceId() throws Exception {
        String invalidBody = """
                {
                  "userId": "u_kim_xyz",
                  "deviceId": "",
                  "eventCategory": "page_view",
                  "occurredAt": "2026-04-30T12:00:00",
                  "eventData": null
                }
                """;

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /events — eventCategory 누락은 400")
    void collectRejectsMissingEventCategory() throws Exception {
        String invalidBody = """
                {
                  "userId": "u_kim_xyz",
                  "deviceId": "dev_abc789",
                  "occurredAt": "2026-04-30T12:00:00",
                  "eventData": null
                }
                """;

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /events — X-Project-Id 헤더 누락은 400")
    void collectRejectsMissingProjectIdHeader() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isBadRequest());
    }
}
