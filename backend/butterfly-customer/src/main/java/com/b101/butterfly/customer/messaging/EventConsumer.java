package com.b101.butterfly.customer.messaging;

import com.b101.butterfly.customer.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class EventConsumer {

    private static final String GROUP_ID = "customer-event-consumer";

    private final EventRepository eventRepository;

    @KafkaListener(topics = EventProducer.TOPIC, groupId = GROUP_ID)
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

        // TODO: event_daily_agg 증분 (다음 라운드에서 EventDailyAgg + upsert 구현)
    }
}
