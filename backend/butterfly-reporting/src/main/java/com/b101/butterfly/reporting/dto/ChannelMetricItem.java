package com.b101.butterfly.reporting.dto;

/**
 * API 60 응답의 channels 항목 (채널별 성과).
 *
 * <p>MVP는 채널 PUSH 단건만 응답될 수 있음 (EMAIL 확장 전제 설계).
 */
public record ChannelMetricItem(
        String channel,
        long sentCount,
        long deliveredCount,
        long clickedCount,
        long failedCount,
        double deliveryRate,
        double clickRate
) {
}
