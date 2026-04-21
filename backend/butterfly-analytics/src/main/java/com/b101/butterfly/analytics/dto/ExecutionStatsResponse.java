package com.b101.butterfly.analytics.dto;

import java.time.LocalDateTime;

/**
 * 신규 API: GET /api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats
 * 회차별 통계 (ADR-0002, ADR-0003 결과).
 *
 * <p>API 58(/campaigns/{campaignId}/stats)은 회차 누적 합산을 반환하며,
 * 이 DTO는 개별 회차의 상세 집계를 반환한다.
 */
public record ExecutionStatsResponse(
        Long campaignId,
        Long executionId,
        String status,              // SCHEDULED / SENDING / COMPLETED / FAILED / CANCELED
        long totalTarget,           // SCHEDULED 상태에서는 0 (아직 세그먼트 미계산)
        long sentCount,
        long deliveredCount,
        long clickedCount,
        long failedCount,
        double deliveryRate,
        double clickRate,
        LocalDateTime sendStartedAt,
        LocalDateTime sendCompletedAt,
        LocalDateTime metricsComputedAt
) {
}
