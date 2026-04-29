package com.b101.butterfly.campaign.messagetemplate.application;

import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
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
    public Long createMessageTemplate(MessageTemplateCreateCommand command) {
        MessageTemplate newMessageTemplate = messageTemplateCreator.registerMessageTemplate(
                command.projectId(),
                command.memberId(),
                command.messageTemplateName(),
                Channel.valueOf(command.channel()),
                command.messageTemplateBody()
        );
        return newMessageTemplate.getMessageTemplateId();
    }
}
