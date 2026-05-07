package com.b101.butterfly.reporting.report.dto;

import java.math.BigDecimal;

/**
 * 캠페인 단계 펀널의 단일 단계 결과 (S14P31B101-90).
 *
 * <p>비율 의미:
 * <ul>
 *     <li>{@code conversionFromStart} — 첫 단계({@link FunnelStage#SENT}) 대비 도달 비율.
 *         첫 단계가 0이면 null.</li>
 *     <li>{@code conversionFromPrev} — 직전 단계 대비 도달 비율 (단계간 통과율).
 *         첫 단계는 항상 100% (자기 자신 대비), 분모가 0이면 null.</li>
 *     <li>{@code dropOffFromPrev} — 직전 단계에서 본 단계로 못 넘어간 비율 = 100 - conversionFromPrev.
 *         null인 conversionFromPrev에 대응하면 null.</li>
 * </ul>
 *
 * <p>모든 비율은 KPI 정의서 §3에 따라 분모 0이면 null.
 */
public record CampaignFunnelStageRow(
        FunnelStage stage,
        long count,
        BigDecimal conversionFromStart,
        BigDecimal conversionFromPrev,
        BigDecimal dropOffFromPrev
) {
}
