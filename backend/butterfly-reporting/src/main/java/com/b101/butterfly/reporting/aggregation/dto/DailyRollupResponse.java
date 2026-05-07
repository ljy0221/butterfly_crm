package com.b101.butterfly.reporting.aggregation.dto;

import java.time.LocalDate;

/**
 * daily 롤업 처리 결과 DTO.
 */
public record DailyRollupResponse(
        LocalDate targetDate,
        int campaignDailyRows,
        int templateDailyRows,
        int failureDailyRows,
        int projectDailyRows,
        long elapsedMs
) {
}
