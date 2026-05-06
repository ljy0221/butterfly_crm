package com.b101.butterfly.campaign.lifecycle.application;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 일반 필드 수정 책임 (DRAFT 상태에서만).
 *
 * <p>비즈니스 규칙(DRAFT 외 거부, 빈값 거부)은 도메인 메서드 {@link Campaign#update}가 캡슐화한다.
 * 본 클래스는 영속 엔티티 로드 + 소유권 검증 + 도메인 위임만 담당.
 */
@Service
@RequiredArgsConstructor
public class CampaignModifier {

    private final CampaignRepository campaignRepository;

    @Transactional
    public Long updateCampaign(
            Long campaignId,
            Long projectId,
            String campaignName,
            String campaignDescription
    ) {
        Campaign campaign = campaignRepository.getByCampaignId(campaignId);
        assertOwnedBy(campaign, projectId);
        campaign.update(campaignName, campaignDescription);
        return campaign.getCampaignId();
    }

    private void assertOwnedBy(Campaign campaign, Long projectId) {
        if (!campaign.isOwnedBy(projectId)) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }
    }
}
