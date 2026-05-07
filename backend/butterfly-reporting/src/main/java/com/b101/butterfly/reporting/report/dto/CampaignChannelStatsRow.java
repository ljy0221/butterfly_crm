package com.b101.butterfly.reporting.report.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * 기간 내 (campaign × channel) 단위로 합산된 발송/반응 카운트 — segment-channel 매트릭스의
 * 입력 행이다. {@link com.b101.butterfly.reporting.aggregation.repository.CampaignStatsDailyRepository}
 * 에서 JPQL로 직접 매핑된다.
 */
public record CampaignChannelStatsRow(
        Long campaignId,
        Channel channel,
        long sentCount,
        long deliveredCount,
        long failedCount,
        long openCount,
        long clickCount,
        long convertCount
) {
}
