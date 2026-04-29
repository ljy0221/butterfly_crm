package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.tenant.entity.Project;
import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.messagetemplate.domain.converter.RequiredVariablesConverter;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.entity.SoftDeletedDomain;
import com.b101.butterfly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "message_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE message_templates SET is_deleted = true, deleted_at = NOW() WHERE message_template_id = ?")
@SQLRestriction("is_deleted = false")
public class MessageTemplate extends SoftDeletedDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_template_id")
    private Long messageTemplateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "message_template_name", nullable = false)
    private String messageTemplateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Column(name = "message_template_body", nullable = false, columnDefinition = "TEXT")
    private String messageTemplateBody;

    @Convert(converter = RequiredVariablesConverter.class)
    @Column(name = "required_variables", columnDefinition = "JSON")
    private List<String> requiredVariables;

    private MessageTemplate(
            Project project,
            Member member,
            String messageTemplateName,
            Channel channel,
            String messageTemplateBody,
            List<String> requiredVariables
    ) {
        this.project = project;
        this.member = member;
        this.messageTemplateName = messageTemplateName;
        this.channel = channel;
        this.messageTemplateBody = messageTemplateBody;
        this.requiredVariables = requiredVariables;
    }

    public static MessageTemplate create(
            Project project,
            Member member,
            String messageTemplateName,
            Channel channel,
            String messageTemplateBody
    ) {
        validate(project, member, messageTemplateName, channel, messageTemplateBody);
        return new MessageTemplate(
                project, member, messageTemplateName, channel, messageTemplateBody,
                MessageTemplateVariableExtractor.extract(messageTemplateBody));
    }

    private static void validate(
            Project project,
            Member member,
            String messageTemplateName,
            Channel channel,
            String messageTemplateBody
    ) {
        if (project == null) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_PROJECT_REQUIRED);
        }
        if (member == null) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_MEMBER_REQUIRED);
        }
        if (channel == null) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_CHANNEL_REQUIRED);
        }
        if (messageTemplateName == null || messageTemplateName.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_NAME_REQUIRED);
        }
        if (messageTemplateBody == null || messageTemplateBody.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_BODY_REQUIRED);
        }
    }

    public void updateBody(String messageTemplateBody) {
        if (messageTemplateBody == null || messageTemplateBody.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_BODY_REQUIRED);
        }
        this.messageTemplateBody = messageTemplateBody;
        this.requiredVariables = MessageTemplateVariableExtractor.extract(messageTemplateBody);
    }

    public void placeTemplateName(String messageTemplateName) {
        if (messageTemplateName == null || messageTemplateName.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_NAME_REQUIRED);
        }
        this.messageTemplateName = messageTemplateName;
    }
}
