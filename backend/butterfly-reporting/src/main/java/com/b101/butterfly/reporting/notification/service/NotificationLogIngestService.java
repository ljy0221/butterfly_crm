package com.b101.butterfly.reporting.notification.service;

import com.b101.butterfly.reporting.notification.dto.NotificationLogIngestRequest;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
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
 * <p>본 서비스는 {@code messageKey} 기반 멱등성만 책임진다. status 전이 규칙(PENDING→SENT 등)
 * 검증은 {@code S14P31B101-175}(122-B)에서 추가된다 — 본 단계에서는 INSERT만 다룬다.
 */
@Service
@RequiredArgsConstructor
public class NotificationLogIngestService {

    private static final Logger log = LoggerFactory.getLogger(NotificationLogIngestService.class);

    private final NotificationLogRepository repository;

    /**
     * 멱등 적재. 같은 {@code messageKey}로 들어온 요청은 INSERT 없이 기존 row를 반환한다.
     *
     * <p>두 단계 가드:
     * <ol>
     *   <li>먼저 {@code findByMessageKey}로 빠르게 hit 여부를 본다.</li>
     *   <li>경합 상황(다중 요청 동시 진입)에서 INSERT가 UNIQUE 제약 위반이면 다시 조회로 복귀한다.</li>
     * </ol>
     */
    @Transactional
    public NotificationLogIngestResult record(NotificationLogIngestRequest request) {
        return repository.findByMessageKey(request.messageKey())
                .map(existing -> {
                    log.debug("notification_logs ingest dedup hit messageKey={}", request.messageKey());
                    return new NotificationLogIngestResult(existing, true);
                })
                .orElseGet(() -> insertNew(request));
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
                .sentAt(request.sentAt())
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
            return new NotificationLogIngestResult(existing, true);
        }
    }
}
