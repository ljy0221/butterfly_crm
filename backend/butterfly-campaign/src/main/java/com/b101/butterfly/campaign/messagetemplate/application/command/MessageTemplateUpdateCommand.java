package com.b101.butterfly.campaign.messagetemplate.application.command;

import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;

public record MessageTemplateUpdateCommand(
        Long projectId,
        Long memberId,
        String messageTemplateTitle,
        String messageTemplateBody,
        String messageTemplateName,
        String messageTemplateDescription,
        String messageTemplateImageUrl,
        String messageChannel
) {

    public MessageTemplate toMessageTemplate() {
        return MessageTemplate.create(
                projectId,
                memberId,
                messageTemplateName,
                messageTemplateDescription,
                Enum.valueOf(Channel.class, messageChannel),
                messageTemplateTitle,
                messageTemplateBody,
                messageTemplateImageUrl
        );
    }
}
