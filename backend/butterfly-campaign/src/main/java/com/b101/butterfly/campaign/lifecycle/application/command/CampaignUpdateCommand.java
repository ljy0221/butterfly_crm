package com.b101.butterfly.campaign.lifecycle.application.command;

public record CampaignUpdateCommand(
        Long campaignId,
        Long projectId,
        String campaignName,
        String campaignDescription
) {
}