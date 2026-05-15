package com.b101.butterfly.notification.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 발송 요청 커맨드.
 * Kafka 메시지: userIds만 채우고 credential/targets는 null → DeliveryService가 자체 조회.
 * 테스트 컨트롤러: credential/targets를 직접 채워서 기존 동작 유지.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryCommand(
        Long projectId,
        Long campaignId,
        Long executionId,
        Channel channel,
        MessagePayload message,
        List<Long> userIds,
        ChannelCredential credential,
        List<DeliveryTarget> targets
) {

    public enum Channel {
        PUSH, EMAIL, KAKAO, SMS, WEBHOOK
    }

    public record MessagePayload(
            String title,
            String body,
            String imageUrl,
            String data
    ) {
        /**
         * 사용자별 변수로 {@code {{var}}} 치환된 새 MessagePayload 를 만든다.
         *
         * <p>치환 대상: title, body, imageUrl. data 는 FCM 메타데이터라 치환 안 함.
         *
         * <p>variables 가 비어 있어도 substitute 는 호출한다 — 템플릿에 {@code {{name}}} 이
         * 남아 있을 때 raw placeholder 노출을 막기 위해 빈 문자열로 치환 + WARN 로그를 남기는
         * fallback policy (VariableSubstitutor 도메인 결정) 를 그대로 따른다. 호출 측은
         * {@link com.b101.butterfly.campaign.messagetemplate.domain.VariableSubstitutor#hasVariablePattern}
         * 으로 한 번만 분기하면 충분.
         */
        public MessagePayload personalize(java.util.Map<String, String> variables) {
            return new MessagePayload(
                    com.b101.butterfly.campaign.messagetemplate.domain.VariableSubstitutor.substitute(title, variables),
                    com.b101.butterfly.campaign.messagetemplate.domain.VariableSubstitutor.substitute(body, variables),
                    com.b101.butterfly.campaign.messagetemplate.domain.VariableSubstitutor.substitute(imageUrl, variables),
                    data
            );
        }
    }

    /**
     * 채널별 인증 정보. sealed interface로 채널마다 필요한 필드만 정의한다.
     * 새 채널 추가 시 record를 하나 추가하고 permits에 등록하면 된다.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = FcmCredential.class, name = "PUSH"),
            @JsonSubTypes.Type(value = SmsCredential.class, name = "SMS"),
            @JsonSubTypes.Type(value = WebhookCredential.class, name = "WEBHOOK"),
            @JsonSubTypes.Type(value = NoCredential.class, name = "NONE")
    })
    public sealed interface ChannelCredential
            permits FcmCredential, SmsCredential, WebhookCredential, NoCredential {}

    public record FcmCredential(String credentialJson) implements ChannelCredential {}

    public record SmsCredential(String apiKey, String apiSecret, String senderNumber) implements ChannelCredential {}

    public record WebhookCredential(String url, String secret, WebhookFormat format) implements ChannelCredential {
        public WebhookCredential(String url, String secret) {
            this(url, secret, WebhookFormat.DEFAULT);
        }
    }

    public enum WebhookFormat {
        DEFAULT, MATTERMOST, SLACK
    }

    /** EMAIL, KAKAO 등 별도 인증이 불필요한 채널용 */
    public record NoCredential() implements ChannelCredential {}

    /**
     * 발송 대상. userId는 필수, 나머지는 채널에 따라 선택.
     *
     * <p>{@code variables} 는 개인화 변수 치환 (S14P31B101-289) — 사용자별 {@code {{var}} → 실제 값}
     * 매핑. worker fan-out 시점에 {@code user_tags} 테이블에서 조회해 채워진다. null 이면 빈 Map 으로
     * 정규화 (compact constructor).
     */
    public record DeliveryTarget(
            Long userId,
            Long deviceId,
            String pushToken,
            String email,
            String phoneNumber,
            Map<String, String> variables
    ) {
        public DeliveryTarget {
            variables = variables != null ? variables : Map.of();
        }

        /** 변수 없는 발송 (레거시/테스트) 용 convenience — variables 를 빈 맵으로 채운다. */
        public DeliveryTarget(Long userId, Long deviceId, String pushToken, String email, String phoneNumber) {
            this(userId, deviceId, pushToken, email, phoneNumber, Map.of());
        }
    }
}
