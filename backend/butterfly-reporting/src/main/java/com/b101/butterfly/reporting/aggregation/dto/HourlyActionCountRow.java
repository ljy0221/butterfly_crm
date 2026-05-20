package com.b101.butterfly.reporting.aggregation.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

public record HourlyActionCountRow(
        Long projectId,
        Long campaignId,
        Channel channel,
        Long openCount,
        Long clickCount,
        Long convertCount
) {
}
