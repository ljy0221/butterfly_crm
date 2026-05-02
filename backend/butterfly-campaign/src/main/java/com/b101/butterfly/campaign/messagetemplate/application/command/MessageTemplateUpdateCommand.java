package com.b101.butterfly.campaign.messagetemplate.application.command;

import com.b101.butterfly.campaign.campaign.domain.enums.Channel;

public record MessageTemplateUpdateCommand(
        Long messageTemplateId,
        Long projectId,
        Long memberId,
        String messageTemplateName,
        String messageTemplateDescription,
        String messageChannel,
        String messageTemplateTitle,
        String messageTemplateBody,
        String messageTemplateImageUrl
) {

    public Channel toChannel() {
        return Channel.valueOf(messageChannel);
    }
}
