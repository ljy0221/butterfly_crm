package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.request.DeviceRegisterRequest;
import com.b101.butterfly.event.dto.response.DeviceRegisterResponse;
import com.b101.butterfly.event.entity.Platform;
import com.b101.butterfly.event.exception.EventValidationExceptionHandler;
import com.b101.butterfly.event.service.DeviceService;
import com.b101.butterfly.global.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("api")
@Import({GlobalExceptionHandler.class, EventValidationExceptionHandler.class})
class DeviceControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private DeviceService deviceService;

    private static final String URL = "/api/v1/devices";
    private static final Long PROJECT_ID = 100L;

    private String validBody() throws Exception {
        DeviceRegisterRequest req = new DeviceRegisterRequest(
                "dev_abc789",
                "u_kim_xyz",
                Platform.ANDROID,
                "",
                "2.1.0",
                "14"
        );
        return objectMapper.writeValueAsString(req);
    }

    @Test
    @DisplayName("POST /devices — 정상 요청은 200 + tokenValid 응답")
    void registerReturnsOk() throws Exception {
        when(deviceService.register(eq(PROJECT_ID), any(DeviceRegisterRequest.class)))
                .thenReturn(new DeviceRegisterResponse(false));

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenValid").value(false));
    }

    @Test
    @DisplayName("POST /devices — pushToken이 비어있지 않으면 tokenValid=true")
    void registerWithRealTokenReturnsTokenValid() throws Exception {
        when(deviceService.register(eq(PROJECT_ID), any(DeviceRegisterRequest.class)))
                .thenReturn(new DeviceRegisterResponse(true));

        String body = """
                {
                  "deviceId": "dev_abc789",
                  "userId": "u_kim_xyz",
                  "platform": "ANDROID",
                  "pushToken": "fcm_token_xyz",
                  "appVersion": "2.1.0",
                  "osVersion": "14"
                }
                """;

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenValid").value(true));
    }

    @Test
    @DisplayName("POST /devices — userId가 null이어도 200 (익명 등록 허용)")
    void registerAllowsNullUserId() throws Exception {
        when(deviceService.register(eq(PROJECT_ID), any(DeviceRegisterRequest.class)))
                .thenReturn(new DeviceRegisterResponse(false));

        String body = """
                {
                  "deviceId": "dev_abc789",
                  "userId": null,
                  "platform": "ANDROID",
                  "pushToken": "",
                  "appVersion": "2.1.0",
                  "osVersion": "14"
                }
                """;

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /devices — deviceId 빈 문자열은 400")
    void registerRejectsBlankDeviceId() throws Exception {
        String body = """
                {
                  "deviceId": "",
                  "userId": "u_kim_xyz",
                  "platform": "ANDROID",
                  "pushToken": "",
                  "appVersion": "2.1.0",
                  "osVersion": "14"
                }
                """;

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /devices — platform 누락은 400")
    void registerRejectsMissingPlatform() throws Exception {
        String body = """
                {
                  "deviceId": "dev_abc789",
                  "userId": "u_kim_xyz",
                  "pushToken": "",
                  "appVersion": "2.1.0",
                  "osVersion": "14"
                }
                """;

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /devices — pushToken null은 400 (sentinel \"\"는 허용해야 함)")
    void registerRejectsNullPushTokenAllowsEmpty() throws Exception {
        String body = """
                {
                  "deviceId": "dev_abc789",
                  "userId": "u_kim_xyz",
                  "platform": "ANDROID",
                  "pushToken": null,
                  "appVersion": "2.1.0",
                  "osVersion": "14"
                }
                """;

        mockMvc.perform(post(URL)
                        .header("X-Project-Id", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /devices — X-Project-Id 헤더 누락은 400")
    void registerRejectsMissingProjectIdHeader() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isBadRequest());
    }
}
