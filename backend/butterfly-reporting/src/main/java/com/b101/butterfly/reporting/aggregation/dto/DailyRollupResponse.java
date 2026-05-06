package com.b101.butterfly.reporting.aggregation.dto;

public record DailyRollupResponse(
        int campaignRows,
        int templateRows,
        int failureRows,
        int projectRows,
        long elapsedMs
) {
}
