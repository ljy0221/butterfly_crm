package com.b101.butterfly.reporting.aggregation.dto;

import java.time.LocalDate;

/**
 * AnalyticsServiceImpl 의 dashboard dailyTrend 쿼리 결과 (S14P31B101-76, ADR-0007).
 *
 * <p>{@code campaign_stats_daily}를 기간/프로젝트로 필터한 후 bucket_date 단위로 합산.
 * sent/delivered/opened/clicked/converted 시계열이 한 점에 모두 담긴다.
 */
public record AnalyticsDailyTrendRow(
        LocalDate date,
        long sent,
        long delivered,
        long opened,
        long clicked,
        long converted
) {
}
