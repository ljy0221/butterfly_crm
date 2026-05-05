package com.b101.butterfly.event.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@Profile("worker")
@RequiredArgsConstructor
public class EventPersistedProducer {

    public static final String TOPIC = "events.persisted";

    private final KafkaTemplate<String, EventPersistedPayload> eventPersistedKafkaTemplate;

    // ADR-0014: AFTER_COMMIT 단계에서 호출. broker ack까지 동기 대기 (실패 시 즉시 IllegalStateException).
    public void send(EventPersistedPayload payload) {
        String partitionKey = payload.projectId() + ":" + payload.userId();
        try {
            eventPersistedKafkaTemplate.send(TOPIC, partitionKey, payload).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Kafka publish failed", e.getCause());
        }
    }
}
