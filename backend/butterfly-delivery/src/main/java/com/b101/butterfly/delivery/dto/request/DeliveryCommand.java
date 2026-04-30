package com.b101.butterfly.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * 발송 요청 커맨드.
 * 캠페인 팀 또는 MQ 리스너에서 생성하여 DeliveryService.deliver()에 전달.
 */
public record DeliveryCommand(
        Long projectId,
        Long campaignId,
        Channel channel,
        MessagePayload message,
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
            String clickAction,
            String data
    ) {}

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
     */
    public record DeliveryTarget(
            Long userId,
            Long deviceId,
            String pushToken,
            String email,
            String phoneNumber
    ) {}
}
