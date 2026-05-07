package com.b101.butterfly.reporting.report.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

import java.time.LocalDate;
import java.util.List;

/**
 * 캠페인 단계 펀널 응답 (S14P31B101-90).
 *
 * <p>{@code stages}는 항상 {@link FunnelStage} 5단계 모두 포함되며, 데이터가 없는 단계도
 * count 0으로 채워져 클라이언트가 빈 칸 처리할 필요가 없게 한다.
 *
 * <p>{@code biggestDropOffStage}는 직전 단계 대비 이탈률이 가장 큰 단계 — 운영자가 어디서
 * 가장 많이 빠지는지 한 눈에 보기 위한 빠른 인사이트. 데이터가 부족하면 null.
 */
public record CampaignFunnelResponse(
        Long projectId,
        Long campaignId,
        Channel channel,
        LocalDate from,
        LocalDate to,
        List<CampaignFunnelStageRow> stages,
        FunnelStage biggestDropOffStage
) {
}
