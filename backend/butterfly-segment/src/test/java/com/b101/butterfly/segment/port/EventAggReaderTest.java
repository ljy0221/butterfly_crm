package com.b101.butterfly.segment.port;

import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.service.preview.EventConditionNotReadyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EventAggReader#userMatches} default 구현 검증.
 *
 * <p>default는 {@code queryMatchingUserIds} 결과 스트림에서 userId 일치 여부 검사.
 * event 측 어댑터가 효율적 SQL로 override 권장이지만 미override 시에도 정확히 동작해야 함.
 */
class EventAggReaderTest {

    private static final EventNode SAMPLE_NODE =
            new EventNode(42L, List.of(), new Period.Cumulative(), 1);

    private static final EventQueryContext CTX =
            new EventQueryContext(1L, LocalDate.of(2026, 5, 4), ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("default userMatches — 결과에 userId 포함 시 true")
    void userMatches_true() {
        EventAggReader reader =
                (node, ctx) -> Stream.of(10L, 20L, 30L);

        assertThat(reader.userMatches(SAMPLE_NODE, 20L, CTX)).isTrue();
    }

    @Test
    @DisplayName("default userMatches — 결과에 userId 미포함 시 false")
    void userMatches_false() {
        EventAggReader reader =
                (node, ctx) -> Stream.of(10L, 20L, 30L);

        assertThat(reader.userMatches(SAMPLE_NODE, 99L, CTX)).isFalse();
    }

    @Test
    @DisplayName("default userMatches — 빈 결과 시 false")
    void userMatches_emptyStream() {
        EventAggReader reader = (node, ctx) -> Stream.empty();

        assertThat(reader.userMatches(SAMPLE_NODE, 1L, CTX)).isFalse();
    }

    @Test
    @DisplayName("default userMatches — 호출 후 stream close (try-with-resources 검증)")
    void userMatches_streamClosed() {
        boolean[] closed = {false};
        EventAggReader reader =
                (node, ctx) -> Stream.of(1L, 2L).onClose(() -> closed[0] = true);

        reader.userMatches(SAMPLE_NODE, 1L, CTX);

        assertThat(closed[0]).isTrue();
    }

    @Test
    @DisplayName("default materializeEventMatchesIntoSegment — 미override 시 EventConditionNotReadyException")
    void materialize_defaultThrows() {
        EventAggReader reader = (node, ctx) -> Stream.empty();

        assertThatThrownBy(() -> reader.materializeEventMatchesIntoSegment(42L, SAMPLE_NODE, CTX))
                .isInstanceOf(EventConditionNotReadyException.class);
    }
}
