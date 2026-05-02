package com.b101.butterfly.event.messaging;

import com.b101.butterfly.event.repository.EventDailyAggRepository;
import com.b101.butterfly.event.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock private EventRepository eventRepository;
    @Mock private EventDailyAggRepository eventDailyAggRepository;

    @InjectMocks private EventConsumer eventConsumer;

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 4, 30, 12, 0);

    private EventPayload payload() {
        return new EventPayload(
                "uuid-1", 100L, 7L, 42L, 1L,
                OCCURRED_AT, 1, "{}"
        );
    }

    @Test
    @DisplayName("consume — 신규 insert(1) 시 daily agg upsert 호출")
    void consumeInsertsThenAggregatesWhenNew() {
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
    }

    @Test
    @DisplayName("consume — 중복(insert=0) 시 daily agg 호출 안 함 (멱등 보장)")
    void consumeSkipsAggregationOnDuplicate() {
        when(eventRepository.insertIfNotExists(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(0);

        eventConsumer.consume(payload());

        verify(eventDailyAggRepository, never()).upsertIncrement(
                any(), any(), any(), any(), any()
        );
    }
}
