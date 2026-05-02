package com.b101.butterfly.campaign.campaign.application.result;

import com.b101.butterfly.campaign.campaign.domain.Campaign;
import com.b101.butterfly.campaign.campaign.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
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