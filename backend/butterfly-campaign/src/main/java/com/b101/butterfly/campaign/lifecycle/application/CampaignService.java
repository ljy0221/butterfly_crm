package com.b101.butterfly.campaign.lifecycle.application;

import com.b101.butterfly.campaign.lifecycle.application.command.CampaignCreateCommand;
import com.b101.butterfly.campaign.lifecycle.application.command.CampaignStatusTransitionCommand;
import com.b101.butterfly.campaign.lifecycle.application.command.CampaignUpdateCommand;
import com.b101.butterfly.campaign.lifecycle.application.result.CampaignDetailResult;
import com.b101.butterfly.campaign.lifecycle.application.result.CampaignListResult;
import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.application.CampaignActivator;
import com.b101.butterfly.campaign.lifecycle.application.CampaignCreator;
import com.b101.butterfly.campaign.lifecycle.application.CampaignModifier;
import com.b101.butterfly.campaign.lifecycle.application.CampaignRemover;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignSpecifications;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.lifecycle.port.SegmentReadinessPort;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 application 서비스 — 도메인 서비스(Creator/Modifier/Remover/Activator)들을 조립한다.
 *
 * <p>비즈니스 분기 로직(상태 검증, 권한 등)은 도메인 메서드가 캡슐화하므로
 * 본 클래스는 command → 도메인 메서드 위임만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignCreator creator;
    private final CampaignModifier modifier;
    private final CampaignRemover remover;
    private final CampaignActivator activator;
    private final SegmentReadinessPort segmentReadinessPort;

    @Transactional
    public Long createCampaign(CampaignCreateCommand command) {
        return creator.createImmediateCampaign(
                command.projectId(),
                command.memberId(),
                command.messageTemplateId(),
                command.segmentId(),
                command.campaignName(),
                command.campaignDescription()
        );
    }

    @Transactional(readOnly = true)
    public Page<CampaignListResult> getCampaignList(
            Long projectId,
            CampaignStatus status,
            Channel channel,
            String q,
            Pageable pageable
    ) {
        Specification<Campaign> spec =
                CampaignSpecifications.withFilters(projectId, status, channel, q);
        return campaignRepository.findAll(spec, pageable).map(CampaignListResult::from);
    }

    @Transactional(readOnly = true)
    public CampaignDetailResult getCampaign(Long projectId, Long campaignId) {
        Campaign campaign = campaignRepository.getByCampaignId(campaignId);
        if (!campaign.isOwnedBy(projectId)) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }
        // segmentId가 있으면 segmentBuildStatus 조회 (Targeting.All이면 null)
        String segmentBuildStatus = null;
        if (campaign.getSegmentId() != null) {
            segmentBuildStatus = segmentReadinessPort.findStatus(campaign.getSegmentId())
                    .map(v -> v.buildStatus())
                    .orElse(null);
        }
        return CampaignDetailResult.from(campaign, segmentBuildStatus);
    }

    @Transactional
    public Long updateCampaign(CampaignUpdateCommand command) {
        return modifier.updateCampaign(command);
    }

    @Transactional
    public void deleteCampaign(Long campaignId, Long projectId) {
        remover.deleteCampaign(campaignId, projectId);
    }

    /**
     * 상태 전이를 단일 진입점으로 처리한다.
     *
     * <p>각 전이는 도메인 메서드(activate/pause/resume/complete)에 위임 — 컨트롤러나 서비스에서
     * status if-else 분기를 두지 않는다. 활성화 시에는 추가 부수효과(Execution 생성, Kafka publish)가
     * {@link CampaignActivator}에서 처리된다.
     */
    @Transactional
    public CampaignStatus transitionStatus(CampaignStatusTransitionCommand command) {
        return switch (command.targetStatus()) {
            case ACTIVE -> handleActivation(command);
            case PAUSED -> handlePause(command);
            case ENDED -> handleComplete(command);
            case DRAFT -> throw new BusinessException(
                    CampaignErrorCode.INVALID_STATUS_TRANSITION);
        };
    }

    private CampaignStatus handleActivation(CampaignStatusTransitionCommand command) {
        Campaign campaign =
                campaignRepository.getByCampaignId(command.campaignId());
        if (!campaign.isOwnedBy(command.projectId())) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }
        if (campaign.getCampaignStatus() == CampaignStatus.DRAFT) {
            activator.activate(command.campaignId(), command.projectId());
            return CampaignStatus.ACTIVE;
        }
        // PAUSED → ACTIVE는 단순 resume (Execution 생성 X — 같은 회차 이어가기)
        campaign.resume();
        return CampaignStatus.ACTIVE;
    }

    private CampaignStatus handlePause(CampaignStatusTransitionCommand command) {
        Campaign campaign = loadOwned(command.campaignId(), command.projectId());
        campaign.pause();
        return CampaignStatus.PAUSED;
    }

    private CampaignStatus handleComplete(CampaignStatusTransitionCommand command) {
        Campaign campaign = loadOwned(command.campaignId(), command.projectId());
        campaign.complete();
        return CampaignStatus.ENDED;
    }

    private Campaign loadOwned(Long campaignId, Long projectId) {
        Campaign campaign = campaignRepository.getByCampaignId(campaignId);
        if (!campaign.isOwnedBy(projectId)) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }
        return campaign;
    }
}