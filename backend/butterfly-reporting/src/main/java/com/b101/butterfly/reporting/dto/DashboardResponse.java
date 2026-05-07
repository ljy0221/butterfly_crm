package com.b101.butterfly.reporting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API 57: GET /api/projects/{projectId}/analytics/dashboard
 * 대시보드 요약 응답.
 *
 * <p>{@code metricsComputedAt}은 집계 기준 시각 — 제안서 4-2 확정 시 추가됨.
 *
 * <p><b>ADR-0007 supersede 적용 (2026-05-07):</b>
 * <ul>
 *   <li>OPEN/CONVERT 카운트 + 비율 4종 추가</li>
 *   <li>모든 비율 필드를 {@code Double}로 변경 — 분모 0이면 {@code null}</li>
 *   <li>{@code avgClickRate} 분모 의미: ADR-0003 {@code clicked/delivered} → ADR-0007 {@code click/open}</li>
 * </ul>
 */
public record DashboardResponse(
        Period period,
        Summary summary,
        List<DailyPoint> dailyTrend,
        LocalDateTime metricsComputedAt
) {

    public record Period(LocalDate from, LocalDate to) {
    }

    /**
     * 기간 요약. 분모 0이면 비율 4종은 모두 {@code null} (KPI 정의서 §3).
     *
     * @param avgDeliveryRate {@code totalDelivered / totalSent}
     * @param avgOpenRate     {@code totalOpen / totalDelivered} — ADR-0007 신규
     * @param avgClickRate    {@code totalClicked / totalOpen} — ADR-0007 분모 변경 (delivered → open)
     * @param avgConvertRate  {@code totalConvert / totalSent} — ADR-0007 신규
     */
    public record Summary(
            long campaignsSentInPeriod,
            long totalSent,
            long totalDelivered,
            long totalOpen,
            long totalClicked,
            long totalConvert,
            long totalFailed,
            Double avgDeliveryRate,
            Double avgOpenRate,
            Double avgClickRate,
            Double avgConvertRate
    ) {
    }

    /**
     * 일자별 추이 한 점. ADR-0007에 따라 OPEN/CONVERT 시계열이 추가됨.
     */
    public record DailyPoint(
            LocalDate date,
            long sent,
            long delivered,
            long opened,
            long clicked,
            long converted
    ) {
    }
}
