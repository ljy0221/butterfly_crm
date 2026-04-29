package com.b101.butterfly.campaign.messagetemplate.application;

import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateCreateCommand;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    private final MessageTemplateCreator messageTemplateCreator;

    @Transactional
    public Long createMessageTemplate(Long memberId, MessageTemplateCreateCommand command) {
        MessageTemplate messageTemplate =
                messageTemplateCreator.registerMessageTemplate(command.toMessageTemplate(memberId));
        return messageTemplate.getMessageTemplateId();
    }
}
