package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageTemplateRemover {

    private final MessageTemplateRepository messageTemplateRepository;
    private final MessageTemplateUsageGuard messageTemplateUsageGuard;

    @Transactional
    public void deleteMessageTemplate(Long messageTemplateId, Long projectId) {
        MessageTemplate template =
                messageTemplateRepository.getByMessageTemplateId(messageTemplateId);
        assertOwnedBy(template, projectId);
        messageTemplateUsageGuard.assertNotInUseByActiveCampaign(messageTemplateId);

        messageTemplateRepository.delete(template);
    }

    private void assertOwnedBy(MessageTemplate template, Long projectId) {
        if (!template.isOwnedBy(projectId)) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_NOT_FOUND);
        }
    }
}
