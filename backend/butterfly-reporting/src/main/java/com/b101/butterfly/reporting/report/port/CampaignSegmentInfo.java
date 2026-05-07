package com.b101.butterfly.reporting.report.port;

/**
 * 캠페인이 타깃하는 세그먼트 메타 — segment-channel 성과 매트릭스(S14P31B101-92)에서 사용한다.
 *
 * <p>reporting 모듈은 campaign/segment 도메인을 직접 import하지 않고 본 record로만 참조한다
 * (ADR-0014 모듈 경계). 실제 어댑터는 butterfly-api 조립 레벨에서 JPQL로 충족한다.
 *
 * <p>{@code segmentId}와 {@code segmentName}이 모두 null이면 "세그먼트 미지정 캠페인"
 * (전체 발송)으로 간주하고 매트릭스에서 별도 행으로 노출한다.
 */
public record CampaignSegmentInfo(
        Long campaignId,
        Long segmentId,
        String segmentName
) {
}
