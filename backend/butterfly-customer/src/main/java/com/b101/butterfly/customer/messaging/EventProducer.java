package com.b101.butterfly.customer.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("api")
@RequiredArgsConstructor
public class EventProducer {

    public static final String TOPIC = "customer.events.collected";

    private final KafkaTemplate<String, EventPayload> kafkaTemplate;

    public void send(EventPayload payload) {
        String partitionKey = payload.projectId() + ":" + payload.userId();
        kafkaTemplate.send(TOPIC, partitionKey, payload);
    }
}
