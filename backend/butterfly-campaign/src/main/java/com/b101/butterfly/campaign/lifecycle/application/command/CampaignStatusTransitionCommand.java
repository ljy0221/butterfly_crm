package com.b101.butterfly.campaign.lifecycle.application.command;

import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;

public record CampaignStatusTransitionCommand(
        Long campaignId,
        Long projectId,
        CampaignStatus targetStatus
) {
}