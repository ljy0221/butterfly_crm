package com.b101.butterfly.reporting.notification.messaging;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Kafka 토픽 {@code notification-send-result} 메시지 페이로드.
 *
 * <p>butterfly-notification 팀(이서연/구동엽)과 합의된 스키마 (S14P31B101-177).
 * {@link com.b101.butterfly.reporting.notification.dto.NotificationLogIngestRequest}와
 * 동일 구조로 통일 — HTTP 대안 경로와 Kafka 경로가 같은 필드셋을 공유한다.
 *
 * <p>알 수 없는 필드는 무시 (@JsonIgnoreProperties) — 발송 팀이 필드를 추가해도
 * 컨슈머가 중단되지 않도록 방어.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationSendResultPayload(
        String messageKey,
        Long projectId,
        Long campaignId,
        Long campaignExecutionId,
        Long messageTemplateId,
        Long userId,
        Long deviceId,
        Channel channel,
        NotificationStatus status,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        String errorCode,
        String errorMessage
) {
}
