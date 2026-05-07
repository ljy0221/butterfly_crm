package com.b101.butterfly.reporting.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Segment × Channel 성과 매트릭스 응답 (S14P31B101-92).
 *
 * <p>{@code rows}는 정렬된 매트릭스 본문, {@code highlights}는 상위/하위 조합 빠른 인사이트.
 */
public record SegmentChannelMatrixResponse(
        Long projectId,
        LocalDate from,
        LocalDate to,
        List<SegmentChannelMatrixRow> rows,
        Highlights highlights
) {

    /**
     * 매트릭스 빠른 인사이트 — 클라이언트가 별도 정렬/계산 없이 바로 띄울 수 있다.
     * 각 항목은 데이터가 부족하면 null.
     */
    public record Highlights(
            SegmentChannelMatrixRow topByOpenRate,
            SegmentChannelMatrixRow topByClickRate,
            SegmentChannelMatrixRow topByConvertRate,
            SegmentChannelMatrixRow bottomByOpenRate
    ) {
    }
}
