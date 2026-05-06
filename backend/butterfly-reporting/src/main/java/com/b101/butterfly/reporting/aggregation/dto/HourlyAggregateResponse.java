package com.b101.butterfly.reporting.aggregation.dto;

/**
 * `POST /internal/stats/aggregate/hourly` 응답 (butterfly_api_spec_v2.md #28).
 */
public record HourlyAggregateResponse(int processedRows, long elapsedMs) {
}
