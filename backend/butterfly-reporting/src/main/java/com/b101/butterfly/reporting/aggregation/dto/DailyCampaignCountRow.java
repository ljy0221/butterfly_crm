package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * daily 롤업용 캠페인×채널 단위 카운트.
 * notification_logs에서 sent/delivered/failed, user_action_logs에서 open/click/convert를 합산한다.
 */
public record DailyCampaignCountRow(
        Long projectId,
        Long campaignId,
        Channel channel,
        Long sentCount,
        Long deliveredCount,
        Long failedCount,
        Long openCount,
        Long clickCount,
        Long convertCount
) {
}
