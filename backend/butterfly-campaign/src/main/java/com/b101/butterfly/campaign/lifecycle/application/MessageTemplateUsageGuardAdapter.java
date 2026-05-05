package com.b101.butterfly.campaign.lifecycle.application;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateUsageGuardPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link MessageTemplateUsageGuardPort}의 lifecycle-side 구현체.
 *
 * <p>messagetemplate BC는 본 어댑터를 모르고 포트만 의존한다 (양방향 BC ACL).
 */
@Component
@RequiredArgsConstructor
public class MessageTemplateUsageGuardAdapter implements MessageTemplateUsageGuardPort {

    private final CampaignRepository campaignRepository;

    @Override
    public void assertNotInUseByActiveCampaign(Long messageTemplateId) {
        campaignRepository.findAllByMessageTemplateId(messageTemplateId)
                .forEach(Campaign::validateTemplateUpdatable);
    }
}
