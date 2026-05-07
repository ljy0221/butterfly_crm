package com.b101.butterfly.reporting.report.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

/**
 * Failure top N 조회용 raw projection — Service에서 ratePercent를 계산해 {@link FailureTopRow}로 변환.
 *
 * <p>JPQL이 본 record로 직접 매핑한다.
 */
public record FailureCountRow(
        Channel channel,
        String errorCode,
        long failedCount,
        int affectedCampaignCount,
        int affectedUserCount
) {
}
