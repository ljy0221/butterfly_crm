package com.b101.butterfly.analytics.dto;

import java.util.List;

/**
 * API 60: GET /api/projects/{projectId}/analytics/channels — 응답 래퍼.
 */
public record ChannelMetricsResponse(List<ChannelMetricItem> channels) {
}
