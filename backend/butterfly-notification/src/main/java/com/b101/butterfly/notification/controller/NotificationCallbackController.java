package com.b101.butterfly.notification.controller;

import com.b101.butterfly.notification.dto.request.ClickedCallbackRequest;
import com.b101.butterfly.notification.dto.request.DeliveredCallbackRequest;
import com.b101.butterfly.notification.dto.response.CallbackResponse;
import com.b101.butterfly.notification.service.NotificationCallbackService;
import com.b101.butterfly.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * SDK 콜백 API.
 * 기기에서 알림 도달/클릭 시 호출. API Key 인증 (X-API-Key 헤더).
 */
@RestController
@Profile("api")
@RequestMapping("/sdk/v1/notifications")
@RequiredArgsConstructor
public class NotificationCallbackController {

    private final NotificationCallbackService notificationCallbackService;

    /**
     * API 55: 도달 확인 콜백
     * POST /sdk/v1/notifications/{logId}/delivered
     */
    @PostMapping("/{logId}/delivered")
    public ApiResponse<CallbackResponse> markDelivered(
            @PathVariable Long logId,
            @RequestBody DeliveredCallbackRequest request) {

        return ApiResponse.ok(notificationCallbackService.markDelivered(logId, request));
    }

    /**
     * API 56: 클릭 확인 ��백
     * POST /sdk/v1/notifications/{logId}/clicked
     */
    @PostMapping("/{logId}/clicked")
    public ApiResponse<CallbackResponse> markClicked(
            @PathVariable Long logId,
            @RequestBody ClickedCallbackRequest request) {

        return ApiResponse.ok(notificationCallbackService.markClicked(logId, request));
    }
}
