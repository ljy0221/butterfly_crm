package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.repository.SegmentRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SegmentBuildErrorRecoverer} 단위 테스트.
 *
 * <p>Kafka {@code DefaultErrorHandler} 가 최종 실패 시 본 recoverer 를 호출한다고 가정하고,
 * recoverer 가 {@code SegmentRepository#forceFailById} 를 정확히 호출하는지 검증.
 */
class SegmentBuildErrorRecovererTest {

    private SegmentRepository segmentRepository;
    private SegmentBuildErrorRecoverer recoverer;

    @BeforeEach
    void setUp() {
        segmentRepository = mock(SegmentRepository.class);
        recoverer = new SegmentBuildErrorRecoverer(segmentRepository);
    }

    @Test
    @DisplayName("SegmentRebuildMessage 최종 실패 시 forceFailById 호출")
    void recoversByForceFail() {
        SegmentRebuildMessage msg =
                new SegmentRebuildMessage(42L, 1L, 17L, Instant.now(), "hash");
        ConsumerRecord<String, SegmentRebuildMessage> record =
                new ConsumerRecord<>("segment.rebuild.requested.v1", 0, 100L, "k", msg);
        when(segmentRepository.forceFailById(42L)).thenReturn(1);

        recoverer.accept(record, new RuntimeException("retry exhausted"));

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(segmentRepository, times(1)).forceFailById(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue()).isEqualTo(42L);
    }

    @Test
    @DisplayName("value 타입이 SegmentRebuildMessage 가 아니면 recovery 스킵")
    void skipsUnknownValueType() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("segment.rebuild.requested.v1", 0, 100L, "k", "garbage");

        recoverer.accept(record, new RuntimeException("oops"));

        verify(segmentRepository, never()).forceFailById(eq(0L));
        verify(segmentRepository, never()).forceFailById(eq(42L));
    }

    @Test
    @DisplayName("forceFailById 자체가 실패해도 listener 추가 retry 방지 위해 swallow")
    void swallowsInnerExceptionToPreventCascade() {
        SegmentRebuildMessage msg =
                new SegmentRebuildMessage(99L, 1L, 17L, Instant.now(), "hash");
        ConsumerRecord<String, SegmentRebuildMessage> record =
                new ConsumerRecord<>("segment.rebuild.requested.v1", 0, 100L, "k", msg);
        when(segmentRepository.forceFailById(99L))
                .thenThrow(new RuntimeException("DB connection error"));

        // 예외 던지지 않아야 함 (listener 무한 retry 방지)
        recoverer.accept(record, new RuntimeException("retry exhausted"));

        verify(segmentRepository, times(1)).forceFailById(99L);
    }
}
