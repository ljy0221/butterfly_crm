package com.b101.butterfly.reporting.notification.messaging;

import com.b101.butterfly.reporting.notification.repository.NotificationLogBulkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Kafka 토픽 {@code notification-send-result} 배치 컨슈머 (S14P31B101-177 / 122-A2).
 *
 * <h3>배치 리스너로 전환한 이유</h3>
 * <p>기존 레코드 단위 컨슈머는 N=30,000 발송 시 30,000번의 개별 INSERT + 커밋을 실행했다.
 * {@code GenerationType.IDENTITY} 키 전략 때문에 Hibernate JDBC 배치({@code batch_size=1000})가
 * 자동으로 비활성화되어, {@code saveAll()} 호출도 결국 1건씩 INSERT했다.
 *
 * <p>해결책: {@link NotificationLogBulkRepository#bulkInsert}를 통해 {@code NamedParameterJdbcTemplate}
 * 로 직접 배치 INSERT를 실행한다. JPA를 우회하므로 IDENTITY 키 제약의 영향을 받지 않는다.
 *
 * <h3>멱등성</h3>
 * <p>{@code INSERT IGNORE} 구문으로 {@code message_key} UNIQUE 위반 시 해당 row만 건너뛴다.
 * Redis 가드는 단일 레코드 처리 시보다 효과가 낮아 제거했다 — DB UNIQUE 제약으로 충분.
 * blank messageKey는 삽입 전 필터링해 UNIQUE 충돌 없이 안전하게 처리한다.
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

    private final NotificationLogBulkRepository bulkRepository;

    @KafkaListener(
            topics = TOPIC,
            groupId = GROUP_ID,
            containerFactory = "notificationSendResultListenerContainerFactory"
    )
    @Transactional
    public void consume(List<NotificationSendResultPayload> payloads) {
        List<NotificationSendResultPayload> valid = payloads.stream()
                .filter(p -> p.messageKey() != null && !p.messageKey().isBlank())
                .toList();

        if (valid.isEmpty()) {
            log.warn("notification-send-result batch: all {} records had blank messageKey — skipped",
                    payloads.size());
            return;
        }

        if (valid.size() < payloads.size()) {
            log.warn("notification-send-result batch: {}/{} records had blank messageKey and were skipped",
                    payloads.size() - valid.size(), payloads.size());
        }

        int[] results = bulkRepository.bulkInsert(valid);

        int inserted = 0;
        for (int r : results) inserted += Math.max(r, 0);
        log.debug("notification-send-result batch: {} received, {} inserted (INSERT IGNORE)",
                valid.size(), inserted);
    }
}
