package com.b101.butterfly.reporting.dto;

/**
 * API 61 응답의 segments 항목 (세그먼트별 성과).
 *
 * <p><b>ADR-0007 supersede 적용 (2026-05-07):</b>
 * OPEN/CONVERT 카운트 + 비율 4종, {@code Double} nullable.
 */
public record SegmentMetricItem(
        Long segmentId,
        String segmentName,
        long campaignCount,
        long sentCount,
        long deliveredCount,
        long openCount,
        long clickedCount,
        long convertCount,
        Double deliveryRate,
        Double openRate,
        Double clickRate,
        Double convertRate
) {
}
