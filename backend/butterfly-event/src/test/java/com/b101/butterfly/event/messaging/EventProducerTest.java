package com.b101.butterfly.event.messaging;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventProducerTest {

    @Mock private KafkaTemplate<String, EventPayload> kafkaTemplate;

    @InjectMocks private EventProducer eventProducer;

    private EventPayload payload() {
        return new EventPayload(
                "uuid-1", 100L, 7L, 42L, 1L,
                LocalDateTime.of(2026, 4, 30, 12, 0), 1, "{}"
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    @DisplayName("send — 토픽/파티션키(projectId:userId)/payload 포맷으로 KafkaTemplate에 위임")
    void sendDelegatesToKafkaTemplateWithExpectedTopicAndKey() {
        EventPayload p = payload();
        SendResult<String, EventPayload> result = new SendResult<>(
                null,
                new RecordMetadata(new TopicPartition(EventProducer.TOPIC, 0), 0, 0, 0, 0, 0)
        );
        when(kafkaTemplate.send(eq(EventProducer.TOPIC), any(String.class), eq(p)))
                .thenReturn(CompletableFuture.completedFuture(result));

        eventProducer.send(p);

        verify(kafkaTemplate).send(EventProducer.TOPIC, "100:7", p);
        assertThat(EventProducer.TOPIC).isEqualTo("events.collected");
    }

    @Test
    @DisplayName("send — 비동기 결과가 ExecutionException이면 IllegalStateException으로 래핑")
    void sendWrapsExecutionExceptionAsIllegalState() {
        EventPayload p = payload();
        CompletableFuture<SendResult<String, EventPayload>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(eq(EventProducer.TOPIC), any(String.class), eq(p)))
                .thenReturn(failed);

        assertThatThrownBy(() -> eventProducer.send(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kafka publish failed");
    }
}
