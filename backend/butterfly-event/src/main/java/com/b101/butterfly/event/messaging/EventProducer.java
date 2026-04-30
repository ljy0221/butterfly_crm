package com.b101.butterfly.event.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@Profile("api")
@RequiredArgsConstructor
public class EventProducer {

  public static final String TOPIC = "events.collected";

  private final KafkaTemplate<String, EventPayload> kafkaTemplate;

  // ADR-0011: publish 성공 = 수락. broker ack까지 동기 대기 후 응답.
  public void send(EventPayload payload) {
    String partitionKey = payload.projectId() + ":" + payload.userId();
    try {
      kafkaTemplate.send(TOPIC, partitionKey, payload).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Kafka publish interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Kafka publish failed", e.getCause());
    }
  }
}
