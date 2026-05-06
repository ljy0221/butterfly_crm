package com.b101.butterfly.reporting.aggregation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DailyRollupRequest(
        @NotNull LocalDate bucketDate,
        boolean force
) {
}
