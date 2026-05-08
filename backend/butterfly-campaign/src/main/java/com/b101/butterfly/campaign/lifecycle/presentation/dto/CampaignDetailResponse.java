package com.b101.butterfly.campaign.lifecycle.presentation.dto;

import com.b101.butterfly.campaign.lifecycle.application.result.CampaignDetailResult;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignType;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.lifecycle.domain.enums.SendMode;
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
        LocalDateTime updatedAt,
        /** 세그먼트 빌드 상태 — Targeting.All이면 null, Targeting.Segment이면 "READY"/"BUILDING" 등. */
        String segmentBuildStatus
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
                result.updatedAt(),
                result.segmentBuildStatus()
        );
    }
}