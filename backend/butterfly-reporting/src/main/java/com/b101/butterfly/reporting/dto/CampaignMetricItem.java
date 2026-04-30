package com.b101.butterfly.reporting.dto;

/**
 * API 59 응답의 content 항목 (캠페인 성과 비교 목록의 한 행).
 */
public record CampaignMetricItem(
        Long campaignId,
        String campaignName,
        String channel,
        long sentCount,
        long deliveredCount,
        long clickedCount,
        long failedCount,
        double deliveryRate,
        double clickRate
) {
}
