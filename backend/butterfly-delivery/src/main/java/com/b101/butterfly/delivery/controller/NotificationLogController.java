package com.b101.butterfly.delivery.controller;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.dto.response.NotificationLogDetailResponse;
import com.b101.butterfly.delivery.dto.response.NotificationLogResponse;
import com.b101.butterfly.delivery.entity.NotificationStatus;
import com.b101.butterfly.delivery.service.NotificationLogService;
import com.b101.butterfly.global.response.ApiResponse;
import com.b101.butterfly.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class NotificationLogController {

    private final NotificationLogService notificationLogService;

    /**
     * API 52: 캠페인 발송 로그 목록
     * GET /api/projects/{projectId}/campaigns/{campaignId}/logs?channel=PUSH&status=SENT&page=0&size=20
     */
    @GetMapping("/campaigns/{campaignId}/logs")
    public ApiResponse<PageResponse<NotificationLogResponse>> getCampaignLogs(
            @PathVariable Long projectId,
            @PathVariable Long campaignId,
            @RequestParam(required = false) DeliveryCommand.Channel channel,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ApiResponse.ok(
                notificationLogService.getCampaignLogs(projectId, campaignId, channel, status, page, size));
    }

    /**
     * API 53: 발송 로그 상세
     * GET /api/projects/{projectId}/logs/{logId}
     */
    @GetMapping("/logs/{logId}")
    public ApiResponse<NotificationLogDetailResponse> getLogDetail(
            @PathVariable Long projectId,
            @PathVariable Long logId) {

        return ApiResponse.ok(notificationLogService.getLogDetail(projectId, logId));
    }

    /**
     * API 54: 유저별 발송 이력
     * GET /api/projects/{projectId}/users/{userId}/logs?page=0&size=20
     */
    @GetMapping("/users/{userId}/logs")
    public ApiResponse<PageResponse<NotificationLogResponse>> getUserLogs(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ApiResponse.ok(
                notificationLogService.getUserLogs(projectId, userId, page, size));
    }
}
