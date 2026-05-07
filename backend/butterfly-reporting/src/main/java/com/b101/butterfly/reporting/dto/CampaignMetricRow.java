package com.b101.butterfly.reporting.dto;

/**
 * API 59 (campaign metrics 페이지) raw projection.
 *
 * <p>{@code campaign_stats_daily}에서 (campaignId, channel) 합산 후 다시 (campaignId)로
 * 묶기 어려우니 (campaignId, MIN(channel))로 대표 채널만 노출하거나 (campaignId)로 GROUP BY +
 * 채널은 첫 row 값 사용. 본 PR은 단순화: (campaignId, channel) 쌍 단위로 그대로 노출
 * — 한 캠페인이 여러 채널에 걸치면 여러 row로 나타남.
 *
 * <p>비율은 Service에서 계산.
 */
public record CampaignMetricRow(
        Long campaignId,
        String channel,
        long sentCount,
        long deliveredCount,
        long openCount,
        long clickedCount,
        long convertCount,
        long failedCount
) {
}
