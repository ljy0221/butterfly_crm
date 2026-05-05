package com.b101.butterfly.campaign.campaign.application.command;

public record CampaignUpdateCommand(
        Long campaignId,
        Long projectId,
        String campaignName,
        String campaignDescription
) {
}