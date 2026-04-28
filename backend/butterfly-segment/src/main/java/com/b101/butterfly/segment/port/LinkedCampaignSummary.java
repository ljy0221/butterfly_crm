package com.b101.butterfly.segment.port;

/**
 * 포트 계층의 캠페인 요약. API DTO {@code dto.response.LinkedCampaignSummary}와 분리해
 * 도메인 모듈이 외부 모듈(campaign) 응답 형식에 강하게 결합되지 않도록 한다.
 */
public record LinkedCampaignSummary(Long campaignId, String name, String campaignStatus) {
}
