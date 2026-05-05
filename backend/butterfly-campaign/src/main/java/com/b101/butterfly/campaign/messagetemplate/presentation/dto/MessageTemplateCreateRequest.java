package com.b101.butterfly.campaign.messagetemplate.presentation.dto;

import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateCreateCommand;
import com.b101.butterfly.campaign.messagetemplate.application.command.MessageTemplateUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 메시지 템플릿 생성/수정 본문.
 *
 * <p>Bean Validation은 도메인 검증과 이중 방어 — 컨트롤러 진입 시점에 400으로 끊어
 * 도메인 호출 비용 절감 + 다중 필드 에러 한 번에 보고. 길이 상한은 DB 컬럼 (V1__core_baseline.sql)에
 * 정렬: name/title 255, description/imageUrl 500. body는 TEXT(64KB) 컬럼이지만 운영 안정성을
 * 위해 application 측 상한 10000자로 제한 (Push/SMS/Email 모두 충분).
 *
 * <p>{@code channel}은 enum 매핑이지만 String으로 받아 {@code MessageChannelResolver}에서 변환
 * (잘못된 값은 도메인에서 BusinessException → 400). 여기에서는 NotBlank만 보장한다.
 */
public record MessageTemplateCreateRequest(
        @NotBlank @Size(max = 255) String messageTemplateName,
        @NotBlank @Size(max = 500) String messageTemplateDescription,
        @NotBlank String channel,
        @NotBlank @Size(max = 255) String messageTemplateTitle,
        @NotBlank @Size(max = 10000) String messageTemplateBody,
        @Size(max = 500) String messageTemplateImageUrl
) {

    public MessageTemplateCreateCommand toCommand(Long projectId, Long memberId) {
        return new MessageTemplateCreateCommand(
                projectId,
                memberId,
                messageTemplateName,
                messageTemplateDescription,
                channel,
                messageTemplateTitle,
                messageTemplateBody,
                messageTemplateImageUrl
        );
    }

    public MessageTemplateUpdateCommand toUpdateCommand(
            Long projectId,
            Long memberId,
            Long messageTemplateId
    ) {
        return new MessageTemplateUpdateCommand(
                messageTemplateId,
                projectId,
                memberId,
                messageTemplateName,
                messageTemplateDescription,
                channel,
                messageTemplateTitle,
                messageTemplateBody,
                messageTemplateImageUrl
        );
    }
}
