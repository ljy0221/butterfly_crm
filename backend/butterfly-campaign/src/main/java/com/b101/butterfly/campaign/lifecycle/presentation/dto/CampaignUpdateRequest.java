package com.b101.butterfly.campaign.lifecycle.presentation.dto;

import com.b101.butterfly.campaign.lifecycle.application.command.CampaignUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 캠페인 수정 본문.
 *
 * <p>PUT-like PATCH semantics — 모든 필드를 항상 명시적으로 전달한다.
 * 변경 안 하려면 기존 값을 그대로 송신 (PATCH "null = 변경 안 함" 의미 충돌 회피).
 *
 * <ul>
 *   <li>campaignName / campaignDescription: NotBlank</li>
 *   <li>messageTemplateId: NotNull (변경 시 channel 자동 재계산)</li>
 *   <li>segmentId: nullable (null = Targeting.All, 명시적 값 = Targeting.Segment)</li>
 * </ul>
 */
public record CampaignUpdateRequest(
        @NotBlank @Size(max = 255) String campaignName,
        @NotBlank @Size(max = 500) String campaignDescription,
        @NotNull @Positive Long messageTemplateId,
        @Positive Long segmentId
) {

    public CampaignUpdateCommand toCommand(Long projectId, Long campaignId) {
        return new CampaignUpdateCommand(
                campaignId,
                projectId,
                campaignName,
                campaignDescription,
                messageTemplateId,
                segmentId);
    }
}
