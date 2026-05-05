package com.b101.butterfly.campaign.lifecycle.presentation.dto;

import com.b101.butterfly.campaign.lifecycle.application.command.CampaignStatusTransitionCommand;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import jakarta.validation.constraints.NotNull;

/**
 * PATCH /campaigns/{id}/status 의 본문.
 *
 * <p>본문에 {@code status} 외 다른 필드가 들어와도 무시되지 않고 거부되도록 record는 단일 필드로 한정.
 * Bean Validation으로 null 거부.
 */
public record StatusTransitionRequest(
        @NotNull CampaignStatus status
) {

    public CampaignStatusTransitionCommand toCommand(Long projectId, Long campaignId) {
        return new CampaignStatusTransitionCommand(campaignId, projectId, status);
    }
}