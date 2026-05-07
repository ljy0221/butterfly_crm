package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * AnalyticsServiceImpl 의 campaign 단위 합산 쿼리 결과 (S14P31B101-76, ADR-0007).
 *
 * <p>{@code campaign_stats_daily}를 기간/프로젝트로 필터한 후 (campaignId, channel) 단위로
 * 합산. AnalyticsService 가 campaignName 매핑을 SPI로 채운 뒤 응답을 조립한다.
 */
public record AnalyticsCampaignStatsRow(
        Long campaignId,
        Channel channel,
        long sentCount,
        long deliveredCount,
        long openCount,
        long clickedCount,
        long convertCount,
        long failedCount
) {
}
