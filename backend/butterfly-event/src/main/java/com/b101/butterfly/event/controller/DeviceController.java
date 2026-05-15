package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.request.DeviceRegisterRequest;
import com.b101.butterfly.event.dto.response.DeviceRegisterResponse;
import com.b101.butterfly.event.service.DeviceService;
import com.b101.butterfly.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("api")
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    /** {@code ApiKeyAuthenticationFilter.RESOLVED_PROJECT_ID} 와 동일한 키.
     *  event 모듈은 butterfly-api 의존이 없어 상수 문자열을 그대로 사용 — 양쪽 변경 시 동기화 필요. */
    private static final String RESOLVED_PROJECT_ID_ATTR = "resolvedProjectId";

    private final DeviceService deviceService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceRegisterResponse>> register(
            @Valid @RequestBody DeviceRegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        Long projectId = (Long) httpRequest.getAttribute(RESOLVED_PROJECT_ID_ATTR);
        DeviceRegisterResponse response = deviceService.register(projectId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
