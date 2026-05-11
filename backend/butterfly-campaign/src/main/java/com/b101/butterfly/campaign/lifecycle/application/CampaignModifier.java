package com.b101.butterfly.campaign.lifecycle.application;

import com.b101.butterfly.campaign.lifecycle.application.command.CampaignUpdateCommand;
import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 수정 책임 (DRAFT 상태에서만).
 *
 * <p>비즈니스 규칙(DRAFT 외 거부, 빈값 거부, messageTemplateId 필수)은 도메인 메서드
 * {@link Campaign#update}가 캡슐화한다. 본 클래스는 영속 엔티티 로드 + 소유권 검증 +
 * 새 template 조회·소유권 검증 + 도메인 위임만 담당.
 *
 * <p>channel 도출: messageTemplateId 변경 시 새 template 의 channel 을 도메인에 전달
 * (CampaignCreator 와 동일 정책).
 */
@Service
@RequiredArgsConstructor
public class CampaignModifier {

    private final CampaignRepository campaignRepository;
    private final MessageTemplateLookupPort messageTemplateLookupPort;

    @Transactional
    public Long updateCampaign(CampaignUpdateCommand cmd) {
        Campaign campaign = campaignRepository.getByCampaignId(cmd.campaignId());
        assertOwnedBy(campaign, cmd.projectId());

        MessageTemplateSnapshot template = messageTemplateLookupPort.lookup(cmd.messageTemplateId());
        assertTemplateOwnedBy(template, cmd.projectId());

        campaign.update(
                cmd.campaignName(),
                cmd.campaignDescription(),
                template.messageTemplateId(),
                template.channel(),
                cmd.segmentId());
        return campaign.getCampaignId();
    }

    private void assertOwnedBy(Campaign campaign, Long projectId) {
        if (!campaign.isOwnedBy(projectId)) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }
    }

    private void assertTemplateOwnedBy(MessageTemplateSnapshot template, Long projectId) {
        if (!projectId.equals(template.projectId())) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED);
        }
    }
}
