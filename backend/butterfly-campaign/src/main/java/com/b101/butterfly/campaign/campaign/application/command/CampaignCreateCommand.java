package com.b101.butterfly.campaign.campaign.application.command;

public record CampaignCreateCommand(
        Long projectId,
        Long memberId,
        Long messageTemplateId,
        Long segmentId,
        String campaignName,
        String campaignDescription
) {
}
