package com.b101.butterfly.reporting.report.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;

import java.time.LocalDate;

/**
 * 캠페인 단계 펀널 조회 쿼리 (S14P31B101-90).
 *
 * <p>{@code projectId}, {@code from}, {@code to}는 필수. 다음 옵션 필터:
 * <ul>
 *     <li>{@code campaignId} — 단일 캠페인 펀널. null이면 프로젝트 전체 합산.</li>
 *     <li>{@code channel} — 특정 채널만. null이면 모든 채널 합산.</li>
 * </ul>
 *
 * <p>본 PR은 정통 자동화 플로우의 노드별 이탈 분석이 아니라 캠페인 발송 lifecycle
 * (SENT → DELIVERED → OPEN → CLICK → CONVERT)의 단계별 drop-off로 스코프 축소
 * (S14P31B101-30 자동화 플로우 도메인 미구현 상태). 자동화 플로우 노드별 펀널은
 * 30번이 머지된 후 후속 티켓에서 추가한다.
 */
public record CampaignFunnelQuery(
        Long projectId,
        LocalDate from,
        LocalDate to,
        Long campaignId,
        Channel channel
) {
}
