package com.b101.butterfly.campaign.lifecycle.presentation.dto;

import com.b101.butterfly.campaign.lifecycle.application.command.CampaignUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CampaignUpdateRequest(
        @NotBlank @Size(max = 255) String campaignName,
        @NotBlank @Size(max = 500) String campaignDescription
) {

    public CampaignUpdateCommand toCommand(Long projectId, Long campaignId) {
        return new CampaignUpdateCommand(
                campaignId, projectId, campaignName, campaignDescription);
    }
}