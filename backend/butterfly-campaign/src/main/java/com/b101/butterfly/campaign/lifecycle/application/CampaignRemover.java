package com.b101.butterfly.campaign.lifecycle.application;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 삭제 책임 (DRAFT, ENDED 상태에서 soft delete).
 *
 * <p>{@code @SQLDelete}가 자동 soft delete 처리.
 */
@Service
@RequiredArgsConstructor
public class CampaignRemover {

    private final CampaignRepository campaignRepository;

    @Transactional
    public void deleteCampaign(Long campaignId, Long projectId) {
        Campaign campaign = campaignRepository.getByCampaignId(campaignId);
        assertOwnedBy(campaign, projectId);
        assertDeletable(campaign);
        campaignRepository.delete(campaign);
    }

    private void assertOwnedBy(Campaign campaign, Long projectId) {
        if (!campaign.isOwnedBy(projectId)) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }
    }

    private void assertDeletable(Campaign campaign) {
        if (!campaign.getCampaignStatus().canDelete()) {
            throw new BusinessException(CampaignErrorCode.NOT_DELETABLE);
        }
    }
}