package com.b101.butterfly.reporting.dto;

import java.time.LocalDateTime;

/**
 * 신규 API: GET /api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats
 * 회차별 통계 (ADR-0002, ADR-0003, ADR-0007 결과).
 *
 * <p>API 58(/campaigns/{campaignId}/stats)은 회차 누적 합산을 반환하며,
 * 이 DTO는 개별 회차의 상세 집계를 반환한다.
 *
 * <p><b>ADR-0007 supersede 적용 (2026-05-07):</b>
 * OPEN/CONVERT 카운트 + 비율 4종, {@code Double} nullable.
 */
public record ExecutionStatsResponse(
        Long campaignId,
        Long executionId,
        String status,              // SCHEDULED / SENDING / COMPLETED / FAILED / CANCELED
        long totalTarget,           // SCHEDULED 상태에서는 0 (아직 세그먼트 미계산)
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
        LocalDateTime sendStartedAt,
        LocalDateTime sendCompletedAt,
        LocalDateTime metricsComputedAt
) {
}
