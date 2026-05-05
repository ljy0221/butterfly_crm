package com.b101.butterfly.campaign.lifecycle.domain;

import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
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

    /**
     * 템플릿이 특정 상태 중 하나라도 갖는 캠페인이 있는지 확인 (count 쿼리, 메모리 로드 X).
     *
     * <p>{@link MessageTemplateUsageGuardPort#assertNotInUseByActiveCampaign}용.
     * findAllByMessageTemplateId로 전체 로드하던 것을 대량 캠페인 환경에서도 OOM 없이 처리.
     */
    boolean existsByMessageTemplateIdAndCampaignStatusIn(
            Long messageTemplateId, List<CampaignStatus> statuses);

    Optional<Campaign> findByCampaignId(Long campaignId);

    Page<Campaign> findAllByProjectId(Long projectId, Pageable pageable);

    default Campaign getByCampaignId(Long campaignId) {
        return findByCampaignId(campaignId)
                .orElseThrow(() -> new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }
}
