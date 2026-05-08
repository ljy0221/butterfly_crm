package com.b101.butterfly.reporting.dto;

/**
 * Dashboard API 57의 기간 요약 raw projection (S14P31B101-202).
 *
 * <p>{@code project_stats_daily}에서 [from, to] 범위 SUM 결과를 매핑한다.
 * COALESCE로 빈 결과 시 0L 반환 — record 생성자가 long 받음.
 *
 * <p>{@code campaignsSentInPeriod}는 기간 내 발송된 캠페인 수 — 본 PR은 단순화를 위해
 * {@code campaign_stats_daily}의 distinct campaign_id 카운트로 산출한다.
 */
public record PeriodSummaryRow(
        long sent,
        long delivered,
        long opened,
        long clicked,
        long converted,
        long failed,
        long campaignsSentInPeriod
) {
    public static PeriodSummaryRow zero() {
        return new PeriodSummaryRow(0, 0, 0, 0, 0, 0, 0);
    }
}
