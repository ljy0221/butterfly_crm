package com.b101.butterfly.campaign.lifecycle.application.result;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import java.time.LocalDateTime;

public record CampaignListResult(
        Long campaignId,
        String campaignName,
        Channel channel,
        CampaignStatus campaignStatus,
        LocalDateTime createdAt
) {

    public static CampaignListResult from(Campaign campaign) {
        return new CampaignListResult(
                campaign.getCampaignId(),
                campaign.getCampaignName(),
                campaign.getChannel(),
                campaign.getCampaignStatus(),
                campaign.getCreatedAt()
        );
    }
}