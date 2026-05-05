package com.b101.butterfly.campaign.campaign.presentation.dto;

import com.b101.butterfly.campaign.campaign.application.command.CampaignUpdateCommand;
import jakarta.validation.constraints.NotBlank;

public record CampaignUpdateRequest(
        @NotBlank String campaignName,
        @NotBlank String campaignDescription
) {

    public CampaignUpdateCommand toCommand(Long projectId, Long campaignId) {
        return new CampaignUpdateCommand(
                campaignId, projectId, campaignName, campaignDescription);
    }
}