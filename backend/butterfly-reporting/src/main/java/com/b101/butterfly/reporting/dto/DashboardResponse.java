package com.b101.butterfly.reporting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API 57: GET /api/projects/{projectId}/analytics/dashboard
 * 대시보드 요약 응답.
 *
 * <p>{@code metricsComputedAt}은 집계 기준 시각 — 제안서 4-2 확정 시 추가됨.
 */
public record DashboardResponse(
        Period period,
        Summary summary,
        List<DailyPoint> dailyTrend,
        LocalDateTime metricsComputedAt
) {

    public record Period(LocalDate from, LocalDate to) {
    }

    public record Summary(
            long campaignsSentInPeriod,   // 기존 totalCampaigns — 제안서 4-3에서 이름 확정
            long totalSent,
            long totalDelivered,
            long totalClicked,
            long totalFailed,
            double avgDeliveryRate,
            double avgClickRate
    ) {
    }

    public record DailyPoint(
            LocalDate date,
            long sent,
            long delivered,
            long clicked
    ) {
    }
}
