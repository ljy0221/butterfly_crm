package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageTemplateModifier {

    private final MessageTemplateRepository messageTemplateRepository;
    private final MessageTemplateUsageGuard messageTemplateUsageGuard;

    @Transactional
    public Long updateMessageTemplate(
            Long messageTemplateId,
            Long projectId,
            String messageTemplateName,
            String messageTemplateDescription,
            Channel channel,
            String messageTemplateTitle,
            String messageTemplateBody,
            String messageTemplateImageUrl
    ) {
        MessageTemplate template =
                messageTemplateRepository.getByMessageTemplateId(messageTemplateId);
        assertOwnedBy(template, projectId);
        messageTemplateUsageGuard.assertNotInUseByActiveCampaign(messageTemplateId);

        template.update(
                messageTemplateName,
                messageTemplateDescription,
                channel,
                messageTemplateTitle,
                messageTemplateBody,
                messageTemplateImageUrl
        );
        return template.getMessageTemplateId();
    }

    private void assertOwnedBy(MessageTemplate template, Long projectId) {
        if (!template.isOwnedBy(projectId)) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_NOT_FOUND);
        }
    }
}
