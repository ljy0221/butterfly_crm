package com.b101.butterfly.campaign.messagetemplate.presentation.dto;

import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateCreateCommand;
import jakarta.validation.constraints.NotBlank;

public record MessageTemplateCreateRequest(
        Long projectId,
        Long memberId,
        @NotBlank
        String messageTemplateName,
        String channel,
        String messageTemplateBody
) {

    public MessageTemplateCreateCommand toCommand() {
        return new MessageTemplateCreateCommand(
                projectId,
                memberId,
                messageTemplateName,
                channel,
                messageTemplateBody
        );
    }
}
