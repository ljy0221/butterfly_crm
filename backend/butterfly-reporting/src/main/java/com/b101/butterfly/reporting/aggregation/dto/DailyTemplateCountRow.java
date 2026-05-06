package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * notification_logs를 message_template_id × channel × date로 GROUP BY한 집계 결과 한 줄.
 */
public record DailyTemplateCountRow(
        Long projectId,
        Long messageTemplateId,
        Channel channel,
        Long usedCampaignCount,
        Long sentCount,
        Long deliveredCount,
        Long openCount,
        Long clickCount,
        Long convertCount
) {
}
