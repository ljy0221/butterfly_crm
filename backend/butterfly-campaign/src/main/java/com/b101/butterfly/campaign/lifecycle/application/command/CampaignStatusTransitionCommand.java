package com.b101.butterfly.campaign.campaign.application.command;

import com.b101.butterfly.campaign.campaign.domain.enums.CampaignStatus;

public record CampaignStatusTransitionCommand(
        Long campaignId,
        Long projectId,
        CampaignStatus targetStatus
) {
}