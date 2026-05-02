package com.b101.butterfly.campaign.campaign.presentation.dto;

import com.b101.butterfly.campaign.campaign.application.command.CampaignCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CampaignCreateRequest(
        @NotNull Long messageTemplateId,
        Long segmentId,
        @NotBlank String campaignName,
        @NotBlank String campaignDescription
) {

    public CampaignCreateCommand toCommand(Long projectId, Long memberId) {
        return new CampaignCreateCommand(
                projectId,
                memberId,
                messageTemplateId,
                segmentId,
                campaignName,
                campaignDescription
        );
    }
}
