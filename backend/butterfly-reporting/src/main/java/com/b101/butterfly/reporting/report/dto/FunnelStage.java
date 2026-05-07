package com.b101.butterfly.reporting.report.dto;

/**
 * 캠페인 단계 펀널의 단계 식별자 (S14P31B101-90).
 *
 * <p>순서가 의미를 가진다 — {@link #ordinal()}이 펀널 단계 인덱스이고, 다음 단계로 가려면
 * 직전 단계가 발생해야 한다 ({@code SENT → DELIVERED → OPEN → CLICK → CONVERT}).
 *
 * <p>각 단계의 카운트 출처:
 * <ul>
 *     <li>{@link #SENT}/{@link #DELIVERED} — {@code campaign_stats_daily.sent_count}/{@code delivered_count}</li>
 *     <li>{@link #OPEN}/{@link #CLICK}/{@link #CONVERT} — {@code campaign_stats_daily.open_count}/{@code click_count}/{@code convert_count}</li>
 * </ul>
 */
public enum FunnelStage {
    SENT,
    DELIVERED,
    OPEN,
    CLICK,
    CONVERT
}
