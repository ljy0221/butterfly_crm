package com.b101.butterfly.notification.messaging;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.TargetOutcome;
import com.b101.butterfly.notification.entity.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 발송 결과를 {@code notification-send-result} 토픽으로 발행.
 * reporting 모듈의 NotificationSendResultConsumer가 소비해 분석용 notification_logs에 적재한다.
 *
 * <p>messageKey = "executionId:userId:deviceId:channel" — consumer 측 UNIQUE 제약 기반 멱등성 보장.
 * executionId가 null인 경우(레거시/테스트 경로)는 발행을 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSendResultPublisher {

    static final String TOPIC = "notification-send-result";

    private final KafkaTemplate<String, NotificationSendResultPayload> notificationSendResultKafkaTemplate;

    public void publishAll(DeliveryCommand command, List<TargetOutcome> outcomes) {
        if (command.executionId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (TargetOutcome outcome : outcomes) {
            DeliveryCommand.DeliveryTarget target = outcome.target();
            boolean success = outcome.result().successful();

            String messageKey = NotificationMessageKey.of(command, target);
            if (messageKey == null) {
                log.warn("notification-send-result 발행 건너뜀: executionId={}, userId={}, deviceId={}, channel={}",
                        command.executionId(), target.userId(), target.deviceId(), command.channel());
                continue;
            }

            NotificationSendResultPayload payload = new NotificationSendResultPayload(
                    messageKey,
                    command.projectId(),
                    command.campaignId(),
                    command.executionId(),
                    null,
                    target.userId(),
                    target.deviceId(),
                    command.channel(),
                    success ? NotificationStatus.SENT : NotificationStatus.FAILED,
                    now,
                    null,
                    success ? null : outcome.result().errorCode(),
                    success ? null : outcome.result().errorMessage()
            );

            notificationSendResultKafkaTemplate.send(TOPIC, command.projectId().toString(), payload);
        }

        log.info("notification-send-result 발행: executionId={}, campaignId={}, count={}",
                command.executionId(), command.campaignId(), outcomes.size());
    }
}
