package com.b101.butterfly.reporting.dto;

import java.util.List;

/**
 * API 61: GET /api/projects/{projectId}/analytics/segments — 응답 래퍼.
 */
public record SegmentMetricsResponse(List<SegmentMetricItem> segments) {
}
