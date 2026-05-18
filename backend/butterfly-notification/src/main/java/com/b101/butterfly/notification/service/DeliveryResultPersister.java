package com.b101.butterfly.notification.service;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.response.DeliveryResult;
import com.b101.butterfly.notification.dto.response.TargetOutcome;
import com.b101.butterfly.notification.entity.CampaignStats;
import com.b101.butterfly.notification.entity.NotificationLog;
import com.b101.butterfly.notification.entity.NotificationStatus;
import com.b101.butterfly.notification.repository.CampaignStatsRepository;
import com.b101.butterfly.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 발송 결과를 트랜잭션 안에서 원자적으로 저장한다.
 * DeliveryServiceImpl에서 분리하여 Spring AOP 프록시가 @Transactional을 인식하도록 함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DeliveryResultPersister {

    private final NotificationLogRepository notificationLogRepository;
    private final CampaignStatsRepository campaignStatsRepository;

    @Transactional
    public void persist(DeliveryCommand command, List<TargetOutcome> outcomes) {
        CampaignStats stats = campaignStatsRepository.findById(command.campaignId())
                .orElseGet(() -> campaignStatsRepository.save(
                        new CampaignStats(command.campaignId(), outcomes.size())
                ));

        List<NotificationLog> logEntries = new ArrayList<>();

        for (TargetOutcome outcome : outcomes) {
            DeliveryCommand.DeliveryTarget target = outcome.target();
            DeliveryResult result = outcome.result();
            LocalDateTime now = LocalDateTime.now();
            String recipient = resolveRecipient(command.channel(), target);

            DeliveryCommand.MessagePayload msg = command.message();
            String title = msg != null ? msg.title() : null;
            String body = msg != null ? msg.body() : null;
            String clickUrl = msg != null ? msg.data() : null;

            if (result.successful()) {
                logEntries.add(NotificationLog.builder()
                        .projectId(command.projectId())
                        .campaignId(command.campaignId())
                        .userId(target.userId())
                        .deviceId(target.deviceId())
                        .channel(command.channel())
                        .recipient(recipient)
                        .title(title)
                        .body(body)
                        .clickUrl(clickUrl)
                        .status(NotificationStatus.SENT)
                        .sentAt(now)
                        .build());
                stats.incrementSent();
            } else {
                logEntries.add(NotificationLog.builder()
                        .projectId(command.projectId())
                        .campaignId(command.campaignId())
                        .userId(target.userId())
                        .deviceId(target.deviceId())
                        .channel(command.channel())
                        .recipient(recipient)
                        .title(title)
                        .body(body)
                        .clickUrl(clickUrl)
                        .status(NotificationStatus.FAILED)
                        .errorCode(result.errorCode())
                        .errorMessage(result.errorMessage())
                        .sentAt(now)
                        .build());
                stats.incrementFailed();

                log.warn("발송 실패: campaignId={}, channel={}, userId={}, error={}",
                        command.campaignId(), command.channel(), target.userId(), result.errorCode());
            }
        }

        notificationLogRepository.saveAll(logEntries);
    }

    private String resolveRecipient(DeliveryCommand.Channel channel, DeliveryCommand.DeliveryTarget target) {
        return switch (channel) {
            case PUSH -> target.pushToken();
            case EMAIL -> target.email();
            case SMS -> target.phoneNumber();
            case WEBHOOK -> null;
            case KAKAO -> null;
        };
    }

}
