package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

import java.time.LocalDateTime;

public record DailyFailureCountRow(
        Long projectId,
        Channel channel,
        String errorCode,
        Long failedCount,
        Long affectedCampaignCnt,
        Long affectedUserCnt,
        LocalDateTime firstOccurredAt,
        LocalDateTime lastOccurredAt
) {
}
