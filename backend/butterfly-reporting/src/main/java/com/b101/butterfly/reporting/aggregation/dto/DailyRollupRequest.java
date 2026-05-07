package com.b101.butterfly.reporting.aggregation.dto;

import java.time.LocalDate;

/**
 * daily 롤업 수동 트리거 요청 DTO.
 *
 * <p>targetDate가 null이면 전날(yesterday)을 기본값으로 사용한다 — 스케줄러 자동 호출과 동일.
 * force=true면 대상 날짜의 기존 row를 삭제 후 재집계(직전 7일 윈도우 무시, 단일 날짜).
 */
public record DailyRollupRequest(
        LocalDate targetDate,
        boolean force
) {
}
