package com.b101.butterfly.notification.messaging;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.entity.NotificationStatus;

import java.time.LocalDateTime;

/**
 * notification-send-result 토픽 발행 페이로드.
 * reporting 모듈의 NotificationSendResultPayload와 동일 스키마 (S14P31B101-177).
 */
public record NotificationSendResultPayload(
        String messageKey,
        Long projectId,
        Long campaignId,
        Long campaignExecutionId,
        Long messageTemplateId,
        Long userId,
        Long deviceId,
        DeliveryCommand.Channel channel,
        NotificationStatus status,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        String errorCode,
        String errorMessage
) {
}
