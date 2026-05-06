package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

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
