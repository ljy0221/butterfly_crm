package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.service.match.ActiveEventSegmentCache;
import com.b101.butterfly.segment.service.match.SegmentEventMatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentEventMatchConsumerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ActiveEventSegmentCache activeEventSegmentCache;
    @Mock private SegmentEventMatchService segmentEventMatchService;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private SegmentEventMatchConsumer consumer;

    private static final EventOccurredMessage MESSAGE =
            new EventOccurredMessage(
                    "evt-uuid-123",
                    1L,
                    42L,
                    7L,
                    LocalDateTime.of(2026, 5, 4, 10, 0));

    @Test
    @DisplayName("정상 흐름 — dedup 성공 + 활성 segment 2개 평가")
    void happyPath() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("event:dedup:evt-uuid-123"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(activeEventSegmentCache.findActiveSegmentIds(1L, 7L))
                .thenReturn(List.of(11L, 12L));
        when(segmentEventMatchService.evaluateAndUpsert(any(), anyLong(), anyLong(), any()))
                .thenReturn(true);

        consumer.consume(MESSAGE);

        verify(segmentEventMatchService)
                .evaluateAndUpsert(1L, 11L, 42L, MESSAGE.occurredAt());
        verify(segmentEventMatchService)
                .evaluateAndUpsert(1L, 12L, 42L, MESSAGE.occurredAt());
    }

    @Test
    @DisplayName("dedup 실패 — 캐시 lookup + 평가 미수행")
    void dedupFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        consumer.consume(MESSAGE);

        verify(activeEventSegmentCache, never()).findActiveSegmentIds(any(), any());
        verify(segmentEventMatchService, never())
                .evaluateAndUpsert(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("활성 segment 0개 — 평가 미수행")
    void noActiveSegment() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(activeEventSegmentCache.findActiveSegmentIds(1L, 7L)).thenReturn(List.of());

        consumer.consume(MESSAGE);

        verify(segmentEventMatchService, never())
                .evaluateAndUpsert(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("한 segment 평가 실패 — 다른 segment는 계속 평가")
    void oneFailureDoesNotBlockOthers() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(activeEventSegmentCache.findActiveSegmentIds(1L, 7L))
                .thenReturn(List.of(11L, 12L, 13L));
        when(segmentEventMatchService.evaluateAndUpsert(any(), eq(11L), anyLong(), any()))
                .thenReturn(true);
        when(segmentEventMatchService.evaluateAndUpsert(any(), eq(12L), anyLong(), any()))
                .thenThrow(new RuntimeException("일시 장애"));
        when(segmentEventMatchService.evaluateAndUpsert(any(), eq(13L), anyLong(), any()))
                .thenReturn(true);

        consumer.consume(MESSAGE);

        verify(segmentEventMatchService, times(3))
                .evaluateAndUpsert(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("dedup TTL = 10분")
    void dedupTtlIsTenMinutes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(activeEventSegmentCache.findActiveSegmentIds(any(), any())).thenReturn(List.of());

        consumer.consume(MESSAGE);

        verify(valueOps).setIfAbsent("event:dedup:evt-uuid-123", "1", Duration.ofMinutes(10));
    }
}
