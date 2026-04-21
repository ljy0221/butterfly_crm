package com.b101.butterfly.analytics.dto;

/**
 * API 61 응답의 segments 항목 (세그먼트별 성과).
 */
public record SegmentMetricItem(
        Long segmentId,
        String segmentName,
        long campaignCount,
        long sentCount,
        long deliveredCount,
        long clickedCount,
        double deliveryRate,
        double clickRate
) {
}
