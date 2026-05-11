package com.b101.butterfly.campaign.messagetemplate.presentation.dto;

import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateDetailResult;
import java.time.LocalDateTime;
import java.util.List;

public record MessageTemplateDetailResponse(
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

    public static MessageTemplateDetailResponse from(MessageTemplateDetailResult result) {
        return new MessageTemplateDetailResponse(
                result.memberName(),
                result.projectName(),
                result.messageTemplateName(),
                result.messageTemplateDescription(),
                result.channel(),
                result.messageTemplateTitle(),
                result.messageTemplateBody(),
                result.messageTemplateImageUrl(),
                result.requiredVariables(),
                result.createdAt(),
                result.updatedAt()
        );
    }

}
