package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
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
            Long projectId,
            Long memberId,
            String messageTemplateName,
            Channel channel,
            String messageTemplateBody
    ) {
        if (!projectExistencePort.existsProject(projectId)) {
            throw new BusinessException(MessageTemplateErrorCode.PROJECT_NOT_FOUND);
        }
        if (!memberExistencePort.existsMember(memberId)) {
            throw new BusinessException(MessageTemplateErrorCode.MEMBER_NOT_FOUND);
        }

        MessageTemplate messageTemplate = MessageTemplate.create(
                projectId, memberId, messageTemplateName, channel, messageTemplateBody);

        return messageTemplateRepository.save(messageTemplate);
    }
}
