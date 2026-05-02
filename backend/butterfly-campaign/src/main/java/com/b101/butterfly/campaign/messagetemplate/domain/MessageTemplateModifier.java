package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.campaign.campaign.domain.Campaign;
import com.b101.butterfly.campaign.campaign.domain.CampaignRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTemplateModifier {

    private final CampaignRepository campaignRepository;
    private final EntityManager entityManager;

    @Transactional
    public Long updateMessageTemplate(MessageTemplate messageTemplate) {
        List<Campaign> campaigns = campaignRepository
                .findAllByMessageTemplate_messageTemplateId(messageTemplate.getMessageTemplateId());
        campaigns.forEach(Campaign::validateTemplateUpdatable);

        log.info("영속성 객체인지 상태 확인: {}", entityManager.contains(messageTemplate));

        messageTemplate.placeMessageTemplate(
                messageTemplate.getMessageTemplateBody(),
                messageTemplate.getMessageTemplateImageUrl(),
                messageTemplate.getMessageTemplateName(),
                messageTemplate.getMessageTemplateDescription(),
                messageTemplate.getMessageTemplateTitle(),
                messageTemplate.getChannel()
        );
        return messageTemplate.getMessageTemplateId();
    }
}
