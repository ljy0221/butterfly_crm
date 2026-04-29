package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.campaign.messagetemplate.port.MemberExistencePort;
import com.b101.butterfly.campaign.messagetemplate.port.ProjectExistencePort;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageTemplateCreator {

    private final MessageTemplateRepository messageTemplateRepository;
    private final ProjectExistencePort projectExistencePort;
    private final MemberExistencePort memberExistencePort;

    public MessageTemplate registerMessageTemplate(
            MessageTemplate messageTemplate
    ) {
        if (!projectExistencePort.existsProject(messageTemplate.getProjectId())) {
            throw new BusinessException(MessageTemplateErrorCode.PROJECT_NOT_FOUND);
        }
        if (!memberExistencePort.existsMember(messageTemplate.getMemberId())) {
            throw new BusinessException(MessageTemplateErrorCode.MEMBER_NOT_FOUND);
        }

        MessageTemplate newMessageTemplate = MessageTemplate.create(
                messageTemplate.getProjectId(),
                messageTemplate.getMemberId(),
                messageTemplate.getMessageTemplateName(),
                messageTemplate.getChannel(),
                messageTemplate.getMessageTemplateBody()
        );

        return messageTemplateRepository.save(newMessageTemplate);
    }
}
