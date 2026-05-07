package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * AnalyticsServiceImpl 의 channels 메트릭 쿼리 결과 (S14P31B101-76, ADR-0007).
 *
 * <p>{@code campaign_stats_daily}를 기간/프로젝트로 필터한 후 channel 단위로 합산.
 */
public record AnalyticsChannelStatsRow(
        Channel channel,
        long sentCount,
        long deliveredCount,
        long openCount,
        long clickedCount,
        long convertCount,
        long failedCount
) {
}
