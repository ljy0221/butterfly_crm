package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 메세지 템플릿이 ACTIVE 캠페인에 사용 중이지 않은지를 검증한다.
 * 수정/삭제 양쪽에서 동일한 정책을 적용하기 위한 단일 책임 컴포넌트.
 */
@Component
@RequiredArgsConstructor
public class MessageTemplateUsageGuard {

    private final CampaignRepository campaignRepository;

    public void assertNotInUseByActiveCampaign(Long messageTemplateId) {
        campaignRepository.findAllByMessageTemplateId(messageTemplateId)
                .forEach(Campaign::validateTemplateUpdatable);
    }
}
