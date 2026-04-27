package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.entity.Project;
import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.entity.SoftDeletedDomain;
import com.b101.butterfly.global.error.BusinessException;
import jakarta.persistence.Column;
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

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Column(name = "message_templates_body", nullable = false, columnDefinition = "TEXT")
    private String messageTemplatesBody;

    @Column(name = "required_variables", columnDefinition = "JSON")
    private String requiredVariables;

    private MessageTemplate(
            Project project,
            Member member,
            String templateName,
            Channel channel,
            String messageTemplatesBody,
            String requiredVariables
    ) {
        this.project = project;
        this.member = member;
        this.templateName = templateName;
        this.channel = channel;
        this.messageTemplatesBody = messageTemplatesBody;
        this.requiredVariables = requiredVariables;
    }

    public static MessageTemplate create(
            Project project,
            Member member,
            String templateName,
            Channel channel,
            String messageTemplatesBody,
            String requiredVariables
    ) {
        validate(project, member, templateName, channel, messageTemplatesBody);
        return new MessageTemplate(
                project, member, templateName, channel, messageTemplatesBody, requiredVariables);
    }

    private static void validate(
            Project project,
            Member member,
            String templateName,
            Channel channel,
            String messageTemplatesBody
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
        if (templateName == null || templateName.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_NAME_REQUIRED);
        }
        if (messageTemplatesBody == null || messageTemplatesBody.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_BODY_REQUIRED);
        }
    }

    public void updateBody(String messageTemplatesBody, String requiredVariables) {
        if (messageTemplatesBody == null || messageTemplatesBody.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_BODY_REQUIRED);
        }
        this.messageTemplatesBody = messageTemplatesBody;
        this.requiredVariables = requiredVariables;
    }

    public void placeTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_NAME_REQUIRED);
        }
        this.templateName = templateName;
    }
}
