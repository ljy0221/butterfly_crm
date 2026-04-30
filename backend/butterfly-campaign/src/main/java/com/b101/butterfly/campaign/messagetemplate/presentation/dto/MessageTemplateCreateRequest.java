package com.b101.butterfly.campaign.messagetemplate.presentation.dto;

import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateCreateCommand;

public record MessageTemplateCreateRequest(
        Long projectId,
        String messageTemplateName,
        String messageTemplateDescription,
        String channel,
        String messageTemplateTitle,
        String messageTemplateBody,
        String messageTemplateImageUrl
) {

    public MessageTemplateCreateCommand toCommand(Long memberId) {
        return new MessageTemplateCreateCommand(
                projectId,
                memberId,
                messageTemplateName,
                messageTemplateDescription,
                channel,
                messageTemplateTitle,
                messageTemplateBody,
                messageTemplateImageUrl
        );
    }
}
