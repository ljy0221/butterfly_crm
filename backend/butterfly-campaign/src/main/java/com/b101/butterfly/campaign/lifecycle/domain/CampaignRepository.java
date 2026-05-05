package com.b101.butterfly.campaign.lifecycle.domain;

import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findAllByMessageTemplateId(Long messageTemplateId);

    Optional<Campaign> findByCampaignId(Long campaignId);

    Page<Campaign> findAllByProjectId(Long projectId, Pageable pageable);

    default Campaign getByCampaignId(Long campaignId) {
        return findByCampaignId(campaignId)
                .orElseThrow(() -> new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }
}
