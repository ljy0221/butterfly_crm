package com.b101.butterfly.campaign.lifecycle.application.result;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignType;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.lifecycle.domain.enums.SendMode;
import java.time.LocalDateTime;

public record CampaignDetailResult(
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

    public static CampaignDetailResult from(Campaign campaign) {
        return new CampaignDetailResult(
                campaign.getCampaignId(),
                campaign.getProjectId(),
                campaign.getMessageTemplateId(),
                campaign.getSegmentId(),
                campaign.getMemberId(),
                campaign.getCampaignName(),
                campaign.getCampaignDescription(),
                campaign.getCampaignType(),
                campaign.getSendMode(),
                campaign.getChannel(),
                campaign.getCampaignStatus(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}