package com.b101.butterfly.event.messaging;

import com.b101.butterfly.event.repository.EventDailyAggRepository;
import com.b101.butterfly.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@Lazy(false)
@Profile("worker")
@RequiredArgsConstructor
public class EventConsumer {

    private static final String GROUP_ID = "events-consumer";

    private final EventRepository eventRepository;
    private final EventDailyAggRepository eventDailyAggRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EventPersistedProducer eventPersistedProducer;

    @KafkaListener(
            topics = EventProducer.TOPIC,
            groupId = GROUP_ID,
            containerFactory = "eventKafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(EventPayload payload) {
        int inserted = eventRepository.insertIfNotExists(
                payload.eventUuid(),
                payload.projectId(),
                payload.userId(),
                payload.categoryId(),
                payload.deviceId(),
                payload.occurredAt(),
                payload.schemaVersion(),
                payload.eventData()
        );

        if (inserted == 0) {
            log.debug("Duplicate event_uuid skipped: {}", payload.eventUuid());
            return;
        }

        eventDailyAggRepository.upsertIncrement(
                payload.occurredAt().toLocalDate(),
                payload.projectId(),
                payload.categoryId(),
                payload.userId(),
                payload.occurredAt()
        );

        // ADR-0014: tx commit 성공 후 events.persisted 토픽으로 publish — segment 실시간 매칭용
        applicationEventPublisher.publishEvent(new EventPersistedPayload(
                payload.eventUuid(),
                payload.projectId(),
                payload.userId(),
                payload.categoryId(),
                payload.occurredAt()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPersisted(EventPersistedPayload payload) {
        try {
            eventPersistedProducer.send(payload);
        } catch (RuntimeException e) {
            // tx 이미 commit됨. 누락은 segment rebuild로 복구 가능 (eventual consistency, ADR-0014)
            log.error("events.persisted publish 실패. eventUuid={}", payload.eventUuid(), e);
        }
    }
}
