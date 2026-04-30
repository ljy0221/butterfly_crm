package com.b101.butterfly.notification.dto.response;

import com.b101.butterfly.notification.entity.NotificationLog;

import java.time.LocalDateTime;

public record NotificationLogDetailResponse(
        Long logId,
        Long projectId,
        Long campaignId,
        Long userId,
        Long deviceId,
        String channel,
        String recipient,
        String status,
        String errorCode,
        String errorMessage,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime clickedAt,
        LocalDateTime createdAt
) {

    public static NotificationLogDetailResponse from(NotificationLog log) {
        return new NotificationLogDetailResponse(
                log.getLogId(),
                log.getProjectId(),
                log.getCampaignId(),
                log.getUserId(),
                log.getDeviceId(),
                log.getChannel() != null ? log.getChannel().name() : null,
                log.getRecipient(),
                log.getStatus().name(),
                log.getErrorCode(),
                log.getErrorMessage(),
                log.getSentAt(),
                log.getDeliveredAt(),
                log.getClickedAt(),
                log.getCreatedAt()
        );
    }
}
