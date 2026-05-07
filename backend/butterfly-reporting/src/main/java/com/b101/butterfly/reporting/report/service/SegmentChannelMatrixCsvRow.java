package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.reporting.report.dto.SegmentChannelMatrixRow;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Segment × Channel 매트릭스 row를 CSV row로 직렬화 (S14P31B101-91 / S14P31B101-92 후속).
 *
 * <p>컬럼 순서는 응답 DTO {@link SegmentChannelMatrixRow}의 필드와 동일:
 * {@code segmentId, segmentName, channel, sentCount, deliveredCount, failedCount,
 *        openCount, clickCount, convertCount, deliveryRate, openRate, clickRate, convertRate}
 *
 * <p>{@code segmentId}/{@code segmentName}이 null이면 "세그먼트 미지정 캠페인" 그룹으로,
 * CSV에서는 빈 문자열로 출력한다 (0과 명확히 구분 — 0은 segmentId 0이 따로 존재할 수 있어 의미가 다름).
 *
 * <p>비율(BigDecimal)은 분모 0인 경우 null로 들어오는 것이 KPI 정의서 §3 규약이며,
 * CSV 직렬화 시 null은 빈 문자열로 출력 — 0%와 명확히 구분.
 */
final class SegmentChannelMatrixCsvRow {

    static final List<String> HEADERS = List.of(
            "segmentId", "segmentName", "channel",
            "sentCount", "deliveredCount", "failedCount",
            "openCount", "clickCount", "convertCount",
            "deliveryRate", "openRate", "clickRate", "convertRate"
    );

    private SegmentChannelMatrixCsvRow() {
    }

    static List<String> toRow(SegmentChannelMatrixRow row) {
        return Arrays.asList(
                row.segmentId() == null ? "" : Long.toString(row.segmentId()),
                row.segmentName() == null ? "" : row.segmentName(),
                row.channel() == null ? "" : row.channel().name(),
                Long.toString(row.sentCount()),
                Long.toString(row.deliveredCount()),
                Long.toString(row.failedCount()),
                Long.toString(row.openCount()),
                Long.toString(row.clickCount()),
                Long.toString(row.convertCount()),
                rate(row.deliveryRate()),
                rate(row.openRate()),
                rate(row.clickRate()),
                rate(row.convertRate())
        );
    }

    /** 분모 0 등으로 비율이 null이면 빈 문자열로 직렬화 (CSV에서 0%와 구분). */
    private static String rate(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
