package com.b101.butterfly.campaign.messagetemplate.presentation.dto;

import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateListResult;
import java.time.LocalDateTime;

public record MessageTemplatesResponse(
        String messageTemplateName,
        String messageTitle,
        String channel,
        String memberName,
        LocalDateTime createdAt
) {

    public static MessageTemplatesResponse from(
            MessageTemplateListResult messageTemplateListResult) {
        return new MessageTemplatesResponse(
                messageTemplateListResult.messageTemplateName(),
                messageTemplateListResult.messageTitle(),
                messageTemplateListResult.channel(),
                messageTemplateListResult.memberName(),
                messageTemplateListResult.createdAt()
        );
    }

}
