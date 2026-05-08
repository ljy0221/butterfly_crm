package com.b101.butterfly.reporting.dto;

import java.time.LocalDate;

/**
 * Dashboard API 57의 dailyTrend 시계열 raw projection (S14P31B101-202).
 *
 * <p>{@code project_stats_daily}에서 (project_id, bucket_date) 단위로 한 row씩 매핑된다.
 */
public record DailyTrendRow(
        LocalDate date,
        long sent,
        long delivered,
        long opened,
        long clicked,
        long converted
) {
}
