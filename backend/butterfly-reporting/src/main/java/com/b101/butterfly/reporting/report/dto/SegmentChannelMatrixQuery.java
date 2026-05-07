package com.b101.butterfly.reporting.report.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

import java.time.LocalDate;
import java.util.Set;

/**
 * Segment × Channel 성과 매트릭스 조회 쿼리 (S14P31B101-92).
 *
 * <p>{@code projectId}, {@code from}, {@code to}는 필수. 나머지는 필터 옵션이다.
 * {@code channels}가 비어 있으면 모든 채널 포함, {@code segmentIds}가 비어 있으면 모든
 * 세그먼트 포함 (미지정 캠페인 행도 결과에 포함).
 *
 * <p>{@link #sortBy}는 응답 행을 어떤 지표로 정렬할지 지정 — null이면 기본 sentCount DESC.
 */
public record SegmentChannelMatrixQuery(
        Long projectId,
        LocalDate from,
        LocalDate to,
        Set<Channel> channels,
        Set<Long> segmentIds,
        SortBy sortBy,
        boolean ascending
) {

    public enum SortBy {
        SENT_COUNT,
        DELIVERED_COUNT,
        OPEN_RATE,
        CLICK_RATE,
        CONVERT_RATE
    }
}
