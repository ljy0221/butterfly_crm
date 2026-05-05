package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * hourly 집계 워커가 notification_logs에서 캠페인×채널 단위로 카운트를 합산해 받는 한 줄 응답.
 *
 * <p>시간 윈도우(bucket)는 호출자가 알고 있는 컨텍스트라 본 record에 포함하지 않는다. UPSERT 시
 * `(projectId, campaignId, channel, bucketHour)` 자연 키로 저장된다.
 */
public record HourlyCountRow(
        Long projectId,
        Long campaignId,
        Channel channel,
        Long pendingCount,
        Long sentCount,
        Long deliveredCount,
        Long failedCount
) {
}
