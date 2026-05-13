package com.b101.butterfly.event.controller;

import com.b101.butterfly.event.dto.request.EventCollectRequest;
import com.b101.butterfly.event.dto.response.EventCollectResponse;
import com.b101.butterfly.event.service.EventCollectService;
import com.b101.butterfly.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("api")
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventCollectController {

    private final EventCollectService eventCollectService;

    // TODO: 정식 인증 통합 시 X-Project-Id 헤더 → API Key 추출로 교체
    @PostMapping
    public ResponseEntity<ApiResponse<EventCollectResponse>> collect(
            @RequestHeader("X-Project-Id") Long projectId,
            @Valid @RequestBody EventCollectRequest request
    ) {
        EventCollectResponse response = eventCollectService.collect(projectId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
