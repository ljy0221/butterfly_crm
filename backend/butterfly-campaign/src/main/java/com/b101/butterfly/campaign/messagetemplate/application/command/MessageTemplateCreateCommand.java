package com.b101.butterfly.campaign.messagetemplate.application.command;

import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;

public record MessageTemplateCreateCommand(
        Long projectId,
        String messageTemplateName,
        String channel,
        String messageTemplateBody
) {

    public MessageTemplate toMessageTemplate(Long memberId) {
        return MessageTemplate.create(
                projectId,
                memberId,
                messageTemplateName,
                Channel.valueOf(channel),
                messageTemplateBody
        );
    }
}
