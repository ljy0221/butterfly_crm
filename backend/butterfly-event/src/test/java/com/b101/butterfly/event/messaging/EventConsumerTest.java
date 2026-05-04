package com.b101.butterfly.event.messaging;

import com.b101.butterfly.event.repository.EventDailyAggRepository;
import com.b101.butterfly.event.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock private EventRepository eventRepository;
    @Mock private EventDailyAggRepository eventDailyAggRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private EventPersistedProducer eventPersistedProducer;

    @InjectMocks private EventConsumer eventConsumer;

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 4, 30, 12, 0);

    private EventPayload payload() {
        return new EventPayload(
                "uuid-1", 100L, 7L, 42L, 1L,
                OCCURRED_AT, 1, "{}"
        );
    }

    @Test
    @DisplayName("consume — 신규 insert(1) 시 daily agg upsert + EventPersistedPayload 발행")
    void consumeInsertsThenAggregatesAndPublishesPersistedPayloadWhenNew() {
        when(eventRepository.insertIfNotExists(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        eventConsumer.consume(payload());

        verify(eventDailyAggRepository).upsertIncrement(
                eq(OCCURRED_AT.toLocalDate()),
                eq(100L),
                eq(1L),
                eq(7L),
                eq(OCCURRED_AT)
        );

        ArgumentCaptor<EventPersistedPayload> captor =
                ArgumentCaptor.forClass(EventPersistedPayload.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        EventPersistedPayload published = captor.getValue();
        assertThat(published.eventUuid()).isEqualTo("uuid-1");
        assertThat(published.projectId()).isEqualTo(100L);
        assertThat(published.userId()).isEqualTo(7L);
        assertThat(published.categoryId()).isEqualTo(1L);
        assertThat(published.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("consume — 중복(insert=0) 시 daily agg / application event 둘 다 호출 안 함 (멱등 보장)")
    void consumeSkipsAggregationAndApplicationEventOnDuplicate() {
        when(eventRepository.insertIfNotExists(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(0);

        eventConsumer.consume(payload());

        verify(eventDailyAggRepository, never()).upsertIncrement(
                any(), any(), any(), any(), any()
        );
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("onPersisted — payload 받으면 persistedProducer로 publish")
    void onPersistedSendsToProducer() {
        EventPersistedPayload payload = new EventPersistedPayload(
                "uuid-1", 100L, 7L, 1L, OCCURRED_AT
        );

        eventConsumer.onPersisted(payload);

        verify(eventPersistedProducer).send(payload);
    }

    @Test
    @DisplayName("onPersisted — producer가 throw해도 swallow + log (tx 이미 commit이라 rollback 불가)")
    void onPersistedSwallowsProducerFailure() {
        EventPersistedPayload payload = new EventPersistedPayload(
                "uuid-1", 100L, 7L, 1L, OCCURRED_AT
        );
        doThrow(new IllegalStateException("kafka down"))
                .when(eventPersistedProducer).send(any());

        assertThatNoException().isThrownBy(() ->
                eventConsumer.onPersisted(payload)
        );
    }
}
