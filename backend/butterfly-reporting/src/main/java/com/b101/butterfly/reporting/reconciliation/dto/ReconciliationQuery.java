package com.b101.butterfly.reporting.reconciliation.dto;

import java.time.LocalDate;

/**
 * 정합성 검증 결과 목록 조회 필터 (API #24).
 *
 * <p>모든 필드는 nullable — null인 항목은 필터에서 제외된다.
 */
public record ReconciliationQuery(
        Long projectId,
        Long campaignId,
        Long executionId,
        Boolean consistent,
        LocalDate startDate,
        LocalDate endDate
) {
}
