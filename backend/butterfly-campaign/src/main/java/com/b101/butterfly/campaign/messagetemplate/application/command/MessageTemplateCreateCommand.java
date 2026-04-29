package com.b101.butterfly.campaign.messagetemplate.application.command;

public record MessageTemplateCreateCommand(
        Long projectId,
        Long memberId,
        String messageTemplateName,
        String channel,
        String messageTemplateBody
) {

}
