package com.b101.butterfly.campaign.messagetemplate.application;

import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateCreateCommand;
import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateDetailResult;
import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateListResult;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateCreator;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateRepository;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.campaign.messagetemplate.port.MemberInformationPort;
import com.b101.butterfly.campaign.messagetemplate.port.ProjectInformationPort;
import com.b101.butterfly.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    private final MessageTemplateCreator messageTemplateCreator;
    private final MessageTemplateRepository messageTemplateRepository;
    private final MemberInformationPort memberInformationPort;
    private final ProjectInformationPort projectInformationPort;

    @Transactional
    public Long createMessageTemplate(MessageTemplateCreateCommand command) {
        return messageTemplateCreator.registerMessageTemplate(command.toMessageTemplate());
    }

    @Transactional(readOnly = true)
    public Page<MessageTemplateListResult> getMessageTemplateList(
            Long projectId,
            Pageable pageable
    ) {
        Page<MessageTemplate> templatePage =
                messageTemplateRepository.findAllByProjectId(projectId, pageable);
        List<Long> memberIds = templatePage.stream()
                .map(MessageTemplate::getMemberId)
                .toList();
        if (memberIds.isEmpty()) {
            throw new BusinessException(MessageTemplateErrorCode.MEMBER_ID_INVALID);
        }

        Map<Long, String> memberMap = memberInformationPort.getMemberNames(memberIds);

        return templatePage.map(templates -> {
            String memberName = memberMap.get(templates.getMemberId());
            return MessageTemplateListResult.of(templates, memberName);
        });
    }

    @Transactional(readOnly = true)
    public MessageTemplateDetailResult getMessageTemplate(Long templateId) {
        MessageTemplate messageTemplate =
                messageTemplateRepository.getByMessageTemplateId(templateId);
        String memberName = memberInformationPort.getMemberName(messageTemplate.getMemberId());
        String projectName = projectInformationPort.findProjectName(messageTemplate.getProjectId());
        return MessageTemplateDetailResult.of(messageTemplate, memberName, projectName);
    }

}
