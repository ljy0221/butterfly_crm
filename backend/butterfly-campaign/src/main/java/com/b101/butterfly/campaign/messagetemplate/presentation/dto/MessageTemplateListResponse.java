package com.b101.butterfly.campaign.messagetemplate.presentation.dto;

import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateListResult;
import java.util.List;
import org.springframework.data.domain.Page;

public record MessageTemplateListResponse(
        List<MessageTemplatesResponse> messageTemplates,
        PageInfoResponse pageInfo
) {

    public static MessageTemplateListResponse from(
            Page<MessageTemplateListResult> resultPage
    ) {
        List<MessageTemplatesResponse> responses = resultPage.getContent().stream()
                .map(MessageTemplatesResponse::from)
                .toList();
        return new MessageTemplateListResponse(
                responses,
                PageInfoResponse.from(resultPage)
        );
    }
}
