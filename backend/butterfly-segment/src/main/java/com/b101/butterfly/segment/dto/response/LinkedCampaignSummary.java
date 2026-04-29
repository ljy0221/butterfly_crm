package com.b101.butterfly.segment.dto.response;

/**
 * 세그먼트에 연결된 캠페인 요약. status는 캠페인 모듈 enum이 합류하기 전까지 String.
 */
public record LinkedCampaignSummary(Long campaignId, String name, String campaignStatus) {
}
