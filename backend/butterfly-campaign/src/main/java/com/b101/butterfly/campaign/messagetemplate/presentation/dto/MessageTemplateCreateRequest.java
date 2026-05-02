package com.b101.butterfly.campaign.messagetemplate.presentation.dto;

import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateCreateCommand;
import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateUpdateCommand;

public record MessageTemplateCreateRequest(
        String messageTemplateName,
        String messageTemplateDescription,
        String channel,
        String messageTemplateTitle,
        String messageTemplateBody,
        String messageTemplateImageUrl
) {

    public MessageTemplateCreateCommand toCommand(Long projectId, Long memberId) {
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

    public MessageTemplateUpdateCommand toUpdateCommand(Long projectId, Long memberId) {
        return new MessageTemplateUpdateCommand(
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
