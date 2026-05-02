package com.b101.butterfly.campaign.messagetemplate.domain;

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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "message_template_name", nullable = false)
    private String messageTemplateName;

    @Column(name = "message_template_description", nullable = false)
    private String messageTemplateDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Column(name = "message_template_title", nullable = false)
    private String messageTemplateTitle;

    @Column(name = "message_template_body", nullable = false, columnDefinition = "TEXT")
    private String messageTemplateBody;

    @Column(name = "message_template_image_url")
    private String messageTemplateImageUrl;

    @Convert(converter = RequiredVariablesConverter.class)
    @Column(name = "required_variables", columnDefinition = "JSON")
    private List<String> requiredVariables;

    private MessageTemplate(
            Long projectId,
            Long memberId,
            String messageTemplateName,
            String messageTemplateDescription,
            Channel channel,
            String messageTemplateTitle,
            String messageTemplateBody,
            String messageTemplateImageUrl,
            List<String> requiredVariables
    ) {
        this.projectId = projectId;
        this.memberId = memberId;
        this.messageTemplateName = messageTemplateName;
        this.messageTemplateDescription = messageTemplateDescription;
        this.channel = channel;
        this.messageTemplateTitle = messageTemplateTitle;
        this.messageTemplateBody = messageTemplateBody;
        this.messageTemplateImageUrl = messageTemplateImageUrl;
        this.requiredVariables = requiredVariables;
    }

    public static MessageTemplate create(
            Long projectId,
            Long memberId,
            String messageTemplateName,
            String messageTemplateDescription,
            Channel channel,
            String messageTemplateTitle,
            String messageTemplateBody,
            String messageTemplateImageUrl
    ) {
        validate(projectId, memberId, messageTemplateName, channel, messageTemplateBody,
                messageTemplateDescription, messageTemplateTitle);
        return new MessageTemplate(
                projectId,
                memberId,
                messageTemplateName,
                messageTemplateDescription,
                channel,
                messageTemplateTitle,
                messageTemplateBody,
                messageTemplateImageUrl,
                MessageTemplateVariableExtractor.extract(messageTemplateBody));
    }

    private static void validate(
            Long projectId,
            Long memberId,
            String messageTemplateName,
            Channel channel,
            String messageTemplateBody,
            String messageTemplateDescription,
            String messageTemplateTitle
    ) {
        if (projectId == null) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_PROJECT_REQUIRED);
        }
        if (memberId == null) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_MEMBER_REQUIRED);
        }
        validateChannel(channel);
        validateMessageTemplateName(messageTemplateName);
        validateMessageTemplateBody(messageTemplateBody);
        validateTemplateDescription(messageTemplateDescription);
        validateMessageTemplateTitle(messageTemplateTitle);
    }

    private static void validateChannel(Channel channel) {
        if (channel == null) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_CHANNEL_REQUIRED);
        }
    }

    private static void validateMessageTemplateBody(String messageTemplateBody) {
        if (messageTemplateBody == null || messageTemplateBody.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_BODY_REQUIRED);
        }
    }

    private static void validateMessageTemplateName(String messageTemplateName) {
        if (messageTemplateName == null || messageTemplateName.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_NAME_REQUIRED);
        }
    }

    private static void validateTemplateDescription(String messageTemplateDescription) {
        if (messageTemplateDescription == null || messageTemplateDescription.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_DESCRIPTION_REQUIRED);
        }
    }

    private static void validateMessageTemplateTitle(String messageTemplateTitle) {
        if (messageTemplateTitle == null || messageTemplateTitle.isBlank()) {
            throw new BusinessException(MessageTemplateErrorCode.TEMPLATE_TITLE_REQUIRED);
        }
    }

    public void update(
            String messageTemplateName,
            String messageTemplateDescription,
            Channel channel,
            String messageTemplateTitle,
            String messageTemplateBody,
            String messageTemplateImageUrl
    ) {
        validateChannel(channel);
        validateMessageTemplateName(messageTemplateName);
        validateTemplateDescription(messageTemplateDescription);
        validateMessageTemplateTitle(messageTemplateTitle);
        validateMessageTemplateBody(messageTemplateBody);

        this.messageTemplateName = messageTemplateName;
        this.messageTemplateDescription = messageTemplateDescription;
        this.channel = channel;
        this.messageTemplateTitle = messageTemplateTitle;
        this.messageTemplateBody = messageTemplateBody;
        this.messageTemplateImageUrl = normalizeImageUrl(messageTemplateImageUrl);
        this.requiredVariables = MessageTemplateVariableExtractor.extract(messageTemplateBody);
    }

    public boolean isOwnedBy(Long projectId) {
        return this.projectId.equals(projectId);
    }

    private static String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return imageUrl;
    }

}
