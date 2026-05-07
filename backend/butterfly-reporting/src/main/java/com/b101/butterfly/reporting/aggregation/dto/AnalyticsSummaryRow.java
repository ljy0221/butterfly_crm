package com.b101.butterfly.reporting.aggregation.dto;

/**
 * AnalyticsServiceImpl 의 dashboard summary 쿼리 결과 (S14P31B101-76, ADR-0007).
 *
 * <p>{@code campaign_stats_daily}를 기간/프로젝트로 필터한 후 모든 row를 합산하고,
 * 캠페인 distinct count도 같이 반환한다. 비율 4종은 서비스 단계에서 계산 (분모 0 ⇒ null).
 */
public record AnalyticsSummaryRow(
        long campaignsSentInPeriod,
        long totalSent,
        long totalDelivered,
        long totalOpen,
        long totalClicked,
        long totalConvert,
        long totalFailed
) {
    public static AnalyticsSummaryRow zero() {
        return new AnalyticsSummaryRow(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }
}
