package com.b101.butterfly.campaign.lifecycle.application;

import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateUsageGuardPort;
import com.b101.butterfly.global.error.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link MessageTemplateUsageGuardPort}의 lifecycle-side 구현체.
 *
 * <p>messagetemplate BC는 본 어댑터를 모르고 포트만 의존한다 (양방향 BC ACL).
 *
 * <p>대량 캠페인 환경에서도 OOM 없도록 exists 쿼리(count) 사용 — 1건이라도 ACTIVE이면
 * fail. 도메인 의도는 {@code Campaign.validateTemplateUpdatable}와 동일.
 */
@Component
@RequiredArgsConstructor
public class MessageTemplateUsageGuardAdapter implements MessageTemplateUsageGuardPort {

    private static final List<CampaignStatus> BLOCKING_STATUSES = List.of(CampaignStatus.ACTIVE);

    private final CampaignRepository campaignRepository;

    @Override
    public void assertNotInUseByActiveCampaign(Long messageTemplateId) {
        boolean inUse = campaignRepository
                .existsByMessageTemplateIdAndCampaignStatusIn(messageTemplateId, BLOCKING_STATUSES);
        if (inUse) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_IS_ACTIVE);
        }
    }
}
