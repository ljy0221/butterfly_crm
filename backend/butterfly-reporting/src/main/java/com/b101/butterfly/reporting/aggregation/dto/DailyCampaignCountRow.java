package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * campaign_stats_hourly를 날짜 단위로 GROUP BY한 집계 결과 한 줄.
 *
 * <p>DailyRollupService가 이 결과를 받아 campaign_stats_daily에 UPSERT한다.
 */
public record DailyCampaignCountRow(
        Long projectId,
        Long campaignId,
        Channel channel,
        Long sentCount,
        Long deliveredCount,
        Long failedCount,
        Long openCount,
        Long clickCount,
        Long convertCount
) {
}
