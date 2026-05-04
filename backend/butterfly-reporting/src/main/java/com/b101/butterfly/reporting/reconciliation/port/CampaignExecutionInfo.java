package com.b101.butterfly.reporting.reconciliation.port;

import com.b101.butterfly.reporting.notification.entity.Channel;

import java.time.LocalDateTime;

/**
 * 정합성 검증에 필요한 회차 메타데이터 — 외부 팀(이서연 / `campaign_executions`) 도메인의 일부지만
 * reporting 모듈은 도메인 객체를 직접 참조하지 않고 본 VO로 받는다.
 *
 * <p>{@code targetCount}는 세그먼트 스냅샷 시점 대상자 수 — `expected_count` 의 출처.
 *
 * <p>실제 어댑터 구현은 S14P31B101-187 (126-B) 에서 외부 팀 인터페이스 합의 후 만든다.
 */
public record CampaignExecutionInfo(
        Long executionId,
        Long campaignId,
        Long projectId,
        Channel channel,
        int targetCount,
        LocalDateTime executedAt
) {
}
