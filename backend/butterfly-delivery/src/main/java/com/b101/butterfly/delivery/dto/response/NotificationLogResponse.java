package com.b101.butterfly.delivery.dto.response;

import com.b101.butterfly.delivery.entity.NotificationLog;

import java.time.LocalDateTime;

public record NotificationLogResponse(
        Long logId,
        Long campaignId,
        Long userId,
        Long deviceId,
        String channel,
        String status,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime clickedAt
) {

    public static NotificationLogResponse from(NotificationLog log) {
        return new NotificationLogResponse(
                log.getLogId(),
                log.getCampaignId(),
                log.getUserId(),
                log.getDeviceId(),
                log.getChannel() != null ? log.getChannel().name() : null,
                log.getStatus().name(),
                log.getSentAt(),
                log.getDeliveredAt(),
                log.getClickedAt()
        );
    }
}
