package com.b101.butterfly.campaign.campaign.presentation.dto;

import com.b101.butterfly.campaign.campaign.application.result.CampaignDetailResult;
import com.b101.butterfly.campaign.campaign.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.campaign.domain.enums.CampaignType;
import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.campaign.domain.enums.SendMode;
import java.time.LocalDateTime;

public record CampaignDetailResponse(
        Long campaignId,
        Long projectId,
        Long messageTemplateId,
        Long segmentId,
        Long memberId,
        String campaignName,
        String campaignDescription,
        CampaignType campaignType,
        SendMode sendMode,
        Channel channel,
        CampaignStatus campaignStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CampaignDetailResponse from(CampaignDetailResult result) {
        return new CampaignDetailResponse(
                result.campaignId(),
                result.projectId(),
                result.messageTemplateId(),
                result.segmentId(),
                result.memberId(),
                result.campaignName(),
                result.campaignDescription(),
                result.campaignType(),
                result.sendMode(),
                result.channel(),
                result.campaignStatus(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}