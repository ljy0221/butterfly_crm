package com.b101.butterfly.campaign.messagetemplate.application;

import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateRepository;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MessageTemplateLookupPort} 의 messagetemplate-side 구현체.
 *
 * <p>lifecycle BC는 본 어댑터를 모르고 포트만 의존한다.
 */
@Component
@RequiredArgsConstructor
public class MessageTemplateLookupAdapter implements MessageTemplateLookupPort {

    private final MessageTemplateRepository messageTemplateRepository;

    @Override
    @Transactional(readOnly = true)
    public MessageTemplateSnapshot lookup(Long messageTemplateId) {
        MessageTemplate template =
                messageTemplateRepository.getByMessageTemplateId(messageTemplateId);
        return new MessageTemplateSnapshot(
                template.getMessageTemplateId(),
                template.getProjectId(),
                template.getChannel(),
                template.getMessageTemplateTitle(),
                template.getMessageTemplateBody(),
                template.getMessageTemplateImageUrl()
        );
    }
}
