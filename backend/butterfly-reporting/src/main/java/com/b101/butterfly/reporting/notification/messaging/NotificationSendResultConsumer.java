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
 * <p><b>S14P31B101-180</b>: Redis SETNX(TTL 7일)로 1차 차단을 추가했다. DB 도달 전에
 * 멱등 hit을 걸러 재시도 폭주 시 부하를 낮춘다. Redis 장애 시에는 fail-open되어 DB
 * UNIQUE에 위임한다. 적재 도중 예외 발생 시 키를 release하여 재시도가 다시 진입할
 * 수 있도록 한다.
 *
 * <p>{@code @Profile("worker")} — butterfly-worker 프로세스에서만 활성화.
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class NotificationSendResultConsumer {

    public static final String TOPIC = "notification-send-result";
    static final String GROUP_ID = "butterfly-analytics-notification-logs";

    private final ReportingNotificationLogRepository notificationLogRepository;
    private final RedisMessageKeyGuard messageKeyGuard;

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

        String messageKey = payload.messageKey();

        if (!messageKeyGuard.tryAcquire(messageKey)) {
            log.debug("Redis guard hit (already processed) messageKey={}", messageKey);
            return;
        }

        try {
            NotificationLog log = NotificationLog.create()
                    .messageKey(messageKey)
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
            // message_key UNIQUE 중복 — 멱등 처리, no-op. Redis 가드를 우회한 경합 케이스이므로
            // 키는 그대로 남겨둔다 (다른 트랜잭션이 INSERT 성공 → 후속 요청도 같은 결과).
            NotificationSendResultConsumer.log.debug(
                    "Duplicate messageKey skipped: {}", messageKey);
        } catch (RuntimeException e) {
            // DB 일시 장애 등 재시도 가능 예외 — Redis 키를 풀어 재시도가 가드를 통과할 수 있게 한다.
            messageKeyGuard.release(messageKey);
            throw e;
        }
    }
}
