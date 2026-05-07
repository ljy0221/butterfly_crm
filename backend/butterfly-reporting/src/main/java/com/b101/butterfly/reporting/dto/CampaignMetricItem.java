package com.b101.butterfly.reporting.dto;

/**
 * API 59 응답의 content 항목 (캠페인 성과 비교 목록의 한 행).
 *
 * <p><b>ADR-0007 supersede 적용 (2026-05-07):</b>
 * <ul>
 *   <li>{@code openCount} / {@code convertCount} 추가</li>
 *   <li>{@code openRate} (open/delivered), {@code convertRate} (convert/sent) 추가</li>
 *   <li>비율 4종 모두 {@code Double} — 분모 0이면 {@code null}</li>
 *   <li>{@code clickRate} 분모: ADR-0003 {@code clicked/delivered} → ADR-0007 {@code click/open}</li>
 * </ul>
 */
public record CampaignMetricItem(
        Long campaignId,
        String campaignName,
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
