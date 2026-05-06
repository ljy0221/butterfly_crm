package com.b101.butterfly.reporting.aggregation.dto;

/**
 * campaign_stats_daily를 project × date로 GROUP BY한 전사 집계 결과.
 */
public record DailyProjectCountRow(
        Long projectId,
        Long totalSent,
        Long totalDelivered,
        Long totalFailed,
        Long totalOpen,
        Long totalClick,
        Long totalConvert,
        Long activeCampaignCount
) {
}
