package com.b101.butterfly.campaign.lifecycle.application;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.campaign.messagetemplate.port.MemberExistencePort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.campaign.messagetemplate.port.ProjectExistencePort;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 생성 책임 (DRAFT 상태로 시작).
 *
 * <p>이번 스코프: 즉시 발송(IMMEDIATE) 캠페인만 — 전체 또는 단일 세그먼트 타겟.
 * 세그먼트 존재 여부는 cross-module 의존이라 본 단계에서는 검증하지 않음 (추후 SegmentExistencePort 도입).
 */
@Service
@RequiredArgsConstructor
public class CampaignCreator {

    private final CampaignRepository campaignRepository;
    private final MessageTemplateLookupPort messageTemplateLookupPort;
    private final ProjectExistencePort projectExistencePort;
    private final MemberExistencePort memberExistencePort;

    @Transactional
    public Long createImmediateCampaign(
            Long projectId,
            Long memberId,
            Long messageTemplateId,
            Long segmentId,
            String campaignName,
            String campaignDescription
    ) {
        assertProjectExists(projectId);
        assertMemberExists(memberId);

        MessageTemplateSnapshot template =
                messageTemplateLookupPort.lookup(messageTemplateId);
        assertTemplateOwnedBy(template, projectId);

        Campaign campaign = (segmentId == null)
                ? Campaign.immediateAll(projectId, template, memberId, campaignName, campaignDescription)
                : Campaign.immediateTarget(
                        projectId, template, memberId, campaignName, campaignDescription, segmentId);

        return campaignRepository.save(campaign).getCampaignId();
    }

    private void assertProjectExists(Long projectId) {
        if (!projectExistencePort.existsProject(projectId)) {
            throw new BusinessException(MessageTemplateErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private void assertMemberExists(Long memberId) {
        if (!memberExistencePort.existsMember(memberId)) {
            throw new BusinessException(MessageTemplateErrorCode.MEMBER_NOT_FOUND);
        }
    }

    private void assertTemplateOwnedBy(MessageTemplateSnapshot template, Long projectId) {
        if (!projectId.equals(template.projectId())) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED);
        }
    }
}
