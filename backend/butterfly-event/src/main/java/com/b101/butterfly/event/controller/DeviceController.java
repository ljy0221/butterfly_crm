package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.request.DeviceRegisterRequest;
import com.b101.butterfly.event.dto.response.DeviceRegisterResponse;
import com.b101.butterfly.event.service.DeviceService;
import com.b101.butterfly.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    // TODO: 정식 인증 통합 시 X-Project-Id 헤더 → API Key 추출로 교체
    @PostMapping
    public ResponseEntity<ApiResponse<DeviceRegisterResponse>> register(
            @RequestHeader("X-Project-Id") Long projectId,
            @Valid @RequestBody DeviceRegisterRequest request
    ) {
        DeviceRegisterResponse response = deviceService.register(projectId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
