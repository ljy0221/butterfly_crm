package com.b101.butterfly.reporting.notification.messaging;

import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka 토픽 {@code notification-send-result} 컨슈머 (S14P31B101-177 / 122-A2).
 *
 * <p><b>범위</b>: 수신 → DTO 역직렬화 → {@code notification_logs} INSERT.
 * status 전이 검증·DLQ·재시도·Redis 보조 멱등성은 122-B(S14P31B101-175) 범위.
 *
 * <p><b>멱등성</b>: {@code message_key} UNIQUE 제약(V13)으로 1차 보장.
 * 동일 키 재수신 시 {@link DataIntegrityViolationException}을 잡아 no-op 처리한다.
 *
 * <p>{@code @Profile("worker")} — butterfly-worker 프로세스에서만 활성화.
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class NotificationSendResultConsumer {

    static final String TOPIC = "notification-send-result";
    static final String GROUP_ID = "butterfly-analytics-notification-logs";

    private final ReportingNotificationLogRepository notificationLogRepository;

    @KafkaListener(
            topics = TOPIC,
            groupId = GROUP_ID,
            containerFactory = "notificationSendResultListenerContainerFactory"
    )
    @Transactional
    public void consume(NotificationSendResultPayload payload) {
        if (payload.messageKey() == null || payload.messageKey().isBlank()) {
            log.warn("notification-send-result message has blank messageKey — skipped");
            return;
        }

        try {
            NotificationLog log = NotificationLog.create()
                    .messageKey(payload.messageKey())
                    .projectId(payload.projectId())
                    .campaignId(payload.campaignId())
                    .campaignExecutionId(payload.campaignExecutionId())
                    .messageTemplateId(payload.messageTemplateId())
                    .userId(payload.userId())
                    .deviceId(payload.deviceId())
                    .channel(payload.channel())
                    .status(payload.status())
                    .sentAt(payload.sentAt())
                    .deliveredAt(payload.deliveredAt())
                    .errorCode(payload.errorCode())
                    .errorMessage(payload.errorMessage())
                    .build();

            notificationLogRepository.save(log);

        } catch (DataIntegrityViolationException e) {
            // message_key UNIQUE 중복 — 멱등 처리, no-op
            NotificationSendResultConsumer.log.debug(
                    "Duplicate messageKey skipped: {}", payload.messageKey());
        }
    }
}
