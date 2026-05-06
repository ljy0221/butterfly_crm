package com.b101.butterfly.campaign.lifecycle.presentation.dto;

import com.b101.butterfly.campaign.lifecycle.application.command.CampaignCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 캠페인 생성 본문.
 *
 * <p>Bean Validation은 도메인 검증과 이중 방어 — 잘못된 값은 컨트롤러 진입 시점에 400으로 끊어
 * 다중 필드 에러를 한 번에 보고. 길이 상한은 DB 컬럼 (V1__core_baseline.sql)에 정렬:
 * campaignName 255, campaignDescription 500.
 */
public record CampaignCreateRequest(
        @NotNull @Positive Long messageTemplateId,
        @Positive Long segmentId,
        @NotBlank @Size(max = 255) String campaignName,
        @NotBlank @Size(max = 500) String campaignDescription
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
