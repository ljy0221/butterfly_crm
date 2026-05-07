package com.b101.butterfly.reporting.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API 58: GET /api/projects/{projectId}/campaigns/{campaignId}/stats
 * 캠페인 누적 통계 (ADR-0002 이후: 모든 회차 합산).
 *
 * <p>회차별 드릴다운은 {@code executions} 요약으로 제공하며, 상세는
 * {@link ExecutionStatsResponse} (회차별 stats API)에서 얻는다.
 *
 * <p><b>ADR-0007 supersede 적용 (2026-05-07):</b>
 * <ul>
 *   <li>OPEN/CONVERT 카운트 + 비율 4종 추가</li>
 *   <li>비율 4종 모두 {@code Double} — 분모 0이면 {@code null}</li>
 *   <li>{@code clickRate} 분모: ADR-0003 {@code clicked/delivered} → ADR-0007 {@code click/open}</li>
 * </ul>
 */
public record CampaignStatsResponse(
        Long campaignId,
        long totalTarget,
        long sentCount,
        long deliveredCount,
        long openCount,
        long clickedCount,
        long convertCount,
        long failedCount,
        Double deliveryRate,
        Double openRate,
        Double clickRate,
        Double convertRate,
        List<ExecutionSummary> executions,
        LocalDateTime metricsComputedAt
) {

    /** 회차별 요약 — 프런트가 회차 클릭 시 드릴다운 API를 호출할 수 있도록 최소 정보만 포함. */
    public record ExecutionSummary(
            Long executionId,
            String status,
            LocalDateTime sendStartedAt,
            long sentCount,
            long deliveredCount,
            long openCount,
            long clickedCount,
            long convertCount
    ) {
    }
}
