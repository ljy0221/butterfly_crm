package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * daily 롤업용 user_action_logs 캠페인×채널 단위 액션 카운트.
 * notification_logs에서 집계한 DailyCampaignCountRow와 campaignId+channel 키로 병합된다.
 */
public record DailyActionCountRow(
        Long campaignId,
        Long openCount,
        Long clickCount,
        Long convertCount
) {
}
