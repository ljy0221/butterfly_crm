package com.b101.butterfly.reporting.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * API 60 (channel metrics) raw projection.
 *
 * <p>{@code campaign_stats_daily}에서 (project_id, channel) GROUP BY 결과를 매핑.
 * 비율은 Service에서 계산.
 */
public record ChannelMetricRow(
        Channel channel,
        long sentCount,
        long deliveredCount,
        long openCount,
        long clickedCount,
        long convertCount,
        long failedCount
) {
}
