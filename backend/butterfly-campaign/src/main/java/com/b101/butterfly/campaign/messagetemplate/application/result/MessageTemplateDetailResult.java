package com.b101.butterfly.campaign.messagetemplate.application.result;

import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import java.time.LocalDateTime;
import java.util.List;

public record MessageTemplateDetailResult(
        String memberName,
        String projectName,
        String messageTemplateName,
        String messageTemplateDescription,
        String channel,
        String messageTemplateTitle,
        String messageTemplateBody,
        String messageTemplateImageUrl,
        List<String> requiredVariables,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MessageTemplateDetailResult of(MessageTemplate messageTemplate, String memberName,
            String projectName) {
        return new MessageTemplateDetailResult(
                memberName,
                projectName,
                messageTemplate.getMessageTemplateName(),
                messageTemplate.getMessageTemplateDescription(),
                messageTemplate.getChannel().toString(),
                messageTemplate.getMessageTemplateTitle(),
                messageTemplate.getMessageTemplateBody(),
                messageTemplate.getMessageTemplateImageUrl(),
                messageTemplate.getRequiredVariables(),
                messageTemplate.getCreatedAt(),
                messageTemplate.getUpdatedAt()
        );
    }
}
