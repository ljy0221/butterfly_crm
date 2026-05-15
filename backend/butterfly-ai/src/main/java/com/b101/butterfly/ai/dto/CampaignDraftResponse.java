package com.b101.butterfly.ai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;
import java.util.regex.Pattern;

public record CampaignDraftResponse(
        CampaignDraft campaign,
        TemplateDraft template,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> notes) {

    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public CampaignDraftResponse {
        campaign = campaign == null ? new CampaignDraft(null, null, null, null) : campaign;
        template = template == null ? new TemplateDraft(null, null, null, null, null, null, null) : template;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public record CampaignDraft(
            String campaignName,
            String campaignDescription,
            String campaignType,
            String channel) {

        public CampaignDraft {
            campaignName = defaultText(campaignName, "AI 캠페인 초안");
            campaignDescription =
                    defaultText(campaignDescription, "AI가 생성한 캠페인 설명 초안입니다.");
            campaignType = defaultText(campaignType, "ONE_TIME");
            channel = defaultText(channel, "KAKAO");
        }
    }

    public record TemplateDraft(
            String messageTemplateName,
            String messageTemplateDescription,
            String channel,
            String messageTemplateTitle,
            String messageTemplateBody,
            String messageTemplateImageUrl,
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<String> requiredVariables) {

        public TemplateDraft {
            messageTemplateName = defaultText(messageTemplateName, "AI 메시지 템플릿");
            messageTemplateDescription =
                    defaultText(messageTemplateDescription, "캠페인 목적에 맞춘 메시지 템플릿 초안입니다.");
            channel = defaultText(channel, "KAKAO");
            messageTemplateTitle = defaultText(messageTemplateTitle, "새로운 혜택을 확인해 보세요");
            messageTemplateBody = defaultText(messageTemplateBody, "{{name}}님을 위한 안내입니다.");
            requiredVariables = extractVariables(messageTemplateBody);
        }
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static List<String> extractVariables(String body) {
        return TEMPLATE_VARIABLE_PATTERN
                .matcher(body)
                .results()
                .map(result -> result.group(1))
                .distinct()
                .toList();
    }
}
