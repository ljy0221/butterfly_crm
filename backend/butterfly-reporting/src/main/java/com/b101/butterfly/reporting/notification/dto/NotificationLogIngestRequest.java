package com.b101.butterfly.reporting.notification.dto;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * `POST /internal/notification-logs` 요청 본문 (butterfly_api_spec_v2.md #30).
 *
 * <p>Kafka 토픽이 가용하지 않을 때 발송 워커가 직접 호출하는 HTTP 대안 경로의 페이로드.
 * Kafka 컨슈머 DTO가 도입되면(S14P31B101-177) 동일 형태로 통일한다.
 *
 * <p>{@code messageKey}는 멱등 키 — 같은 키로 두 번 호출하면 기존 logId가 그대로 반환된다.
 */
public record NotificationLogIngestRequest(
        @NotBlank String messageKey,
        @NotNull Long projectId,
        @NotNull Long campaignId,
        Long campaignExecutionId,
        Long messageTemplateId,
        Long userId,
        Long deviceId,
        @NotNull Channel channel,
        @NotNull NotificationStatus status,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        String errorCode,
        String errorMessage
) {
}
