package com.b101.butterfly.campaign.campaign.presentation.dto;

import com.b101.butterfly.campaign.campaign.domain.enums.CampaignStatus;

public record StatusTransitionResponse(
        Long campaignId,
        CampaignStatus campaignStatus
) {
}
