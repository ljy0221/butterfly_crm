package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

import java.time.LocalDateTime;

/**
 * notification_logs(FAILED)를 project × channel × error_code × date로 GROUP BY한 집계 결과.
 */
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
