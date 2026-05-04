package com.b101.butterfly.reporting.audit.dto;

import java.time.LocalDate;

/**
 * 감사 로그 목록 조회 필터 (API #20).
 *
 * <p>모든 필드는 nullable — null인 항목은 필터에서 제외된다.
 * 컨트롤러 레이어가 {@code @RequestParam(required = false)}로 받아 본 record로 묶는다.
 */
public record AuditLogQuery(
        Long projectId,
        Long memberId,
        String action,
        String targetType,
        LocalDate startDate,
        LocalDate endDate
) {
}
