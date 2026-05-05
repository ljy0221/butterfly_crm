package com.b101.butterfly.campaign.lifecycle.application.command;

public record CampaignCreateCommand(
        Long projectId,
        Long memberId,
        Long messageTemplateId,
        Long segmentId,
        String campaignName,
        String campaignDescription
) {
}
