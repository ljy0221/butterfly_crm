package com.b101.butterfly.campaign.messagetemplate.application.result;

import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import java.time.LocalDateTime;

public record MessageTemplateListResult(
        String messageTemplateName,
        String messageTitle,
        String channel,
        String memberName,
        LocalDateTime createdAt
) {

    public static MessageTemplateListResult of(MessageTemplate messageTemplate, String memberName) {
        return new MessageTemplateListResult(
                messageTemplate.getMessageTemplateName(),
                messageTemplate.getMessageTemplateTitle(),
                messageTemplate.getChannel().toString(),
                memberName,
                messageTemplate.getCreatedAt()
        );
    }
}
