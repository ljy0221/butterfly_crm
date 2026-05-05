package com.b101.butterfly.campaign.messagetemplate.application;

import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateCreateCommand;
import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateUpdateCommand;
import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateDetailResult;
import com.b101.butterfly.campaign.messagetemplate.application.result.MessageTemplateListResult;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateCreator;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateModifier;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplateRemover;
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
    private final MessageTemplateModifier messageTemplateModifier;
    private final MessageTemplateRemover messageTemplateRemover;

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
        if (templatePage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> memberIds = templatePage.stream()
                .map(MessageTemplate::getMemberId)
                .distinct()
                .toList();
        Map<Long, String> memberMap = memberInformationPort.getMemberNames(memberIds);

        return templatePage.map(template -> MessageTemplateListResult.of(
                template,
                memberMap.get(template.getMemberId())
        ));
    }

    @Transactional(readOnly = true)
    public MessageTemplateDetailResult getMessageTemplate(Long projectId, Long templateId) {
        MessageTemplate messageTemplate =
                messageTemplateRepository.getByMessageTemplateId(templateId);
        if (!messageTemplate.getProjectId().equals(projectId)) {
            // privacy-preserving: 다른 프로젝트 템플릿 존재 여부를 노출하지 않는다
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_NOT_FOUND);
        }
        String memberName = memberInformationPort.getMemberName(messageTemplate.getMemberId());
        String projectName = projectInformationPort.findProjectName(messageTemplate.getProjectId());
        return MessageTemplateDetailResult.of(messageTemplate, memberName, projectName);
    }

    @Transactional
    public Long updateMessageTemplate(MessageTemplateUpdateCommand command) {
        return messageTemplateModifier.updateMessageTemplate(
                command.messageTemplateId(),
                command.projectId(),
                command.messageTemplateName(),
                command.messageTemplateDescription(),
                command.toChannel(),
                command.messageTemplateTitle(),
                command.messageTemplateBody(),
                command.messageTemplateImageUrl()
        );
    }

    @Transactional
    public void deleteMessageTemplate(Long messageTemplateId, Long projectId) {
        messageTemplateRemover.deleteMessageTemplate(messageTemplateId, projectId);
    }

}
