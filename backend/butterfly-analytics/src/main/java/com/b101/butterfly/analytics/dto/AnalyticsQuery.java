package com.b101.butterfly.analytics.dto;

import java.time.LocalDate;

/**
 * 분석 API 공통 쿼리 파라미터 (from/to 기간 + 선택 필터).
 *
 * <p>타임존은 프로젝트 설정(Asia/Seoul 기본) 기준으로 해석 — 제안서 2-4 참조.
 */
public record AnalyticsQuery(
        LocalDate from,
        LocalDate to,
        String channel,    // optional: PUSH / EMAIL
        Long segmentId     // optional
) {

    public AnalyticsQuery {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be <= to");
        }
    }

    public boolean hasChannelFilter() {
        return channel != null && !channel.isBlank();
    }

    public boolean hasSegmentFilter() {
        return segmentId != null;
    }
}
