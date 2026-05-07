package com.b101.butterfly.reporting.report.service;

import com.b101.butterfly.reporting.report.dto.CampaignFunnelStageRow;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 캠페인 펀널 단계 row를 CSV row로 직렬화 (S14P31B101-91 / S14P31B101-90 후속).
 *
 * <p>컬럼 순서는 명세서/응답 필드와 동일:
 * {@code stage, count, conversionFromStart, conversionFromPrev, dropOffFromPrev}
 *
 * <p>비율(BigDecimal)은 분모 0인 경우 null로 들어오는 것이 KPI 정의서 §3 규약이며,
 * CSV 직렬화 시 null은 빈 문자열로 출력한다 — 0과 명확히 구분되도록.
 */
final class CampaignFunnelCsvRow {

    static final List<String> HEADERS = List.of(
            "stage", "count",
            "conversionFromStart", "conversionFromPrev", "dropOffFromPrev"
    );

    private CampaignFunnelCsvRow() {
    }

    static List<String> toRow(CampaignFunnelStageRow row) {
        return Arrays.asList(
                row.stage() == null ? "" : row.stage().name(),
                Long.toString(row.count()),
                rate(row.conversionFromStart()),
                rate(row.conversionFromPrev()),
                rate(row.dropOffFromPrev())
        );
    }

    /** 분모 0 등으로 비율이 null이면 빈 문자열로 직렬화 (CSV에서 0%와 구분). */
    private static String rate(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
