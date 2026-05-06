package com.b101.butterfly.reporting.aggregation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * `POST /internal/stats/aggregate/hourly` 요청 본문 (butterfly_api_spec_v2.md #28).
 *
 * <p>{@code force=true}는 해당 bucket의 기존 row를 삭제 후 재집계 — 일일적 재처리용.
 */
public record HourlyAggregateRequest(
        @NotNull
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime bucketHour,
        boolean force
) {
}
