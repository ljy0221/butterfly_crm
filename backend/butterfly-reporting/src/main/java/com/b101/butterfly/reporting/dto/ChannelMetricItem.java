package com.b101.butterfly.reporting.dto;

/**
 * API 60 응답의 channels 항목 (채널별 성과).
 *
 * <p>MVP는 채널 PUSH 단건만 응답될 수 있음 (EMAIL 확장 전제 설계).
 *
 * <p><b>ADR-0007 supersede 적용 (2026-05-07):</b>
 * OPEN/CONVERT 카운트 + 비율 4종, {@code Double} nullable.
 */
public record ChannelMetricItem(
        String channel,
        long sentCount,
        long deliveredCount,
        long openCount,
        long clickedCount,
        long convertCount,
        long failedCount,
        Double deliveryRate,
        Double openRate,
        Double clickRate,
        Double convertRate
) {
}
