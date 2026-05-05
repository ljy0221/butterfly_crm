package com.b101.butterfly.campaign.lifecycle.presentation.dto;

import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;

public record StatusTransitionResponse(
        Long campaignId,
        CampaignStatus campaignStatus
) {
}
