package com.b101.butterfly.reporting.notification.service;

import com.b101.butterfly.reporting.notification.dto.NotificationLogIngestRequest;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.entity.NotificationStatus;
import com.b101.butterfly.reporting.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `notification_logs` 적재 서비스. HTTP 대안(`POST /internal/notification-logs`, S14P31B101-176)과
 * 추후 Kafka 컨슈머(S14P31B101-177)가 동일하게 호출한다.
 *
 * <p>책임:
 * <ul>
 *     <li><b>멱등성</b> — 같은 {@code messageKey}로 두 번 들어와도 INSERT는 한 번만 (S14P31B101-176)</li>
 *     <li><b>status 전이 검증</b> — 멱등 hit 시 전이 가능 여부에 따라 UPDATE 또는 no-op (S14P31B101-175)</li>
 *     <li><b>누락 필드 정책</b> — {@code campaignExecutionId}/{@code messageTemplateId} 누락은
 *         WARN 로그 후 적재 진행 (S14P31B101-175)</li>
 * </ul>
 *
 * <p>DLQ/재시도(S14P31B101-180)는 Kafka 컨슈머 레이어에서 처리한다 — 본 서비스는 예외를
 * 그대로 던지고, 컨슈머가 ErrorHandler/RetryTopic으로 받아낸다.
 */
@Service
@RequiredArgsConstructor
public class NotificationLogIngestService {

    private static final Logger log = LoggerFactory.getLogger(NotificationLogIngestService.class);

    private final NotificationLogRepository repository;

    /**
     * 멱등 적재. 같은 {@code messageKey}로 들어온 요청은 INSERT 없이 기존 row를 반환하고,
     * status가 전이 가능하면 UPDATE한다.
     */
    @Transactional
    public NotificationLogIngestResult record(NotificationLogIngestRequest request) {
        warnIfFieldsMissing(request);
        return repository.findByMessageKey(request.messageKey())
                .map(existing -> applyDedupTransition(existing, request))
                .orElseGet(() -> insertNew(request));
    }

    private NotificationLogIngestResult applyDedupTransition(NotificationLog existing,
                                                             NotificationLogIngestRequest request) {
        NotificationStatus current = existing.getStatus();
        NotificationStatus target = request.status();
        if (!current.canTransitionTo(target)) {
            log.warn("notification_logs invalid status transition rejected"
                            + " messageKey={} current={} requested={}",
                    request.messageKey(), current, target);
            return new NotificationLogIngestResult(existing, true);
        }
        if (current == target) {
            log.debug("notification_logs ingest dedup hit (same status) messageKey={}",
                    request.messageKey());
            return new NotificationLogIngestResult(existing, true);
        }
        existing.applyTransition(
                target,
                request.sentAt(),
                request.deliveredAt(),
                request.errorCode(),
                request.errorMessage()
        );
        log.debug("notification_logs status transition applied messageKey={} {}->{}",
                request.messageKey(), current, target);
        return new NotificationLogIngestResult(existing, true);
    }

    private NotificationLogIngestResult insertNew(NotificationLogIngestRequest request) {
        NotificationLog newLog = NotificationLog.create()
                .projectId(request.projectId())
                .campaignId(request.campaignId())
                .campaignExecutionId(request.campaignExecutionId())
                .messageTemplateId(request.messageTemplateId())
                .userId(request.userId())
                .deviceId(request.deviceId())
                .channel(request.channel())
                .status(request.status())
                .errorCode(request.errorCode())
                .errorMessage(request.errorMessage())
                .sentAt(request.sentAt())
                .deliveredAt(request.deliveredAt())
                .messageKey(request.messageKey())
                .build();
        try {
            NotificationLog saved = repository.save(newLog);
            return new NotificationLogIngestResult(saved, false);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 경합으로 다른 트랜잭션이 먼저 INSERT한 케이스 — 다시 조회로 복귀.
            log.info("notification_logs concurrent ingest fell back to dedup messageKey={}",
                    request.messageKey());
            NotificationLog existing = repository.findByMessageKey(request.messageKey())
                    .orElseThrow(() -> e);
            return applyDedupTransition(existing, request);
        }
    }

    private static void warnIfFieldsMissing(NotificationLogIngestRequest request) {
        if (request.campaignExecutionId() == null) {
            log.warn("notification_logs ingest missing campaignExecutionId messageKey={}",
                    request.messageKey());
        }
        if (request.messageTemplateId() == null) {
            log.warn("notification_logs ingest missing messageTemplateId messageKey={}",
                    request.messageKey());
        }
    }
}
