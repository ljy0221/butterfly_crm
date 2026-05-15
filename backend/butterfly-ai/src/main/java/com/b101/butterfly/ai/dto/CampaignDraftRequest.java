package com.b101.butterfly.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CampaignDraftRequest(
        @NotBlank @Size(max = 500) String prompt,
        @Valid CampaignDraftContext context,
        List<@Size(max = 40) String> availableVariables) {

    public CampaignDraftRequest {
        prompt = trim(prompt);
        context = context == null ? new CampaignDraftContext(null, null, null, null, null, null) : context;
        availableVariables = availableVariables == null ? List.of() : List.copyOf(availableVariables);
    }

    public record CampaignDraftContext(
            @Size(max = 50) String campaignName,
            @Size(max = 200) String campaignDescription,
            @Size(max = 30) String campaignType,
            @Size(max = 30) String channel,
            Long segmentId,
            @Size(max = 80) String segmentName) {

        public CampaignDraftContext {
            campaignName = trim(campaignName);
            campaignDescription = trim(campaignDescription);
            campaignType = trim(campaignType);
            channel = trim(channel);
            segmentName = trim(segmentName);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
