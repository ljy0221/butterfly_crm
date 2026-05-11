package com.b101.butterfly.campaign.lifecycle.application.command;

/**
 * 캠페인 수정 명령.
 *
 * <p>DRAFT 상태에서 일반 필드(name/description) 와 발송 대상(messageTemplateId/segmentId)
 * 모두 수정 가능. messageTemplateId 는 항상 명시적으로 전달(변경 안 함이면 기존 ID 그대로),
 * segmentId 는 nullable (null = Targeting.All).
 */
public record CampaignUpdateCommand(
        Long campaignId,
        Long projectId,
        String campaignName,
        String campaignDescription,
        Long messageTemplateId,
        Long segmentId
) {
}
