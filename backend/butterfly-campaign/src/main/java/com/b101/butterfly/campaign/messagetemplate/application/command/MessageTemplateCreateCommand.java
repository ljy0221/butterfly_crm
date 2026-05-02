package com.b101.butterfly.campaign.messagetemplate.application.command;

import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;

public record MessageTemplateCreateCommand(
        Long projectId,
        Long memberId,
        String messageTemplateName,
        String messageTemplateDescription,
        String channel,
        String messageTemplateTitle,
        String messageTemplateBody,
        String messageTemplateImageUrl
) {

    public MessageTemplate toMessageTemplate() {
        return MessageTemplate.create(
                projectId,
                memberId,
                messageTemplateName,
                messageTemplateDescription,
                MessageChannelResolver.resolve(channel),
                messageTemplateTitle,
                messageTemplateBody,
                messageTemplateImageUrl
        );
    }
}
