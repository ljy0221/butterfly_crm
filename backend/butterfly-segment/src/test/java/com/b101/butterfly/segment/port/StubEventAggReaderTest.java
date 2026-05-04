package com.b101.butterfly.segment.port;

import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.port.stub.StubEventAggReader;
import com.b101.butterfly.segment.service.preview.EventConditionNotReadyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stub은 모든 호출을 {@link EventConditionNotReadyException}으로 거절. event 모듈 미합류 시 안전망.
 */
class StubEventAggReaderTest {

    private final StubEventAggReader stub = new StubEventAggReader();

    private static final EventNode SAMPLE_NODE =
            new EventNode(42L, List.of(), new Period.Cumulative(), 1);

    private static final EventQueryContext CTX =
            new EventQueryContext(1L, LocalDate.of(2026, 5, 4), ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("queryMatchingUserIds — EventConditionNotReadyException")
    void query_throws() {
        assertThatThrownBy(() -> stub.queryMatchingUserIds(SAMPLE_NODE, CTX))
                .isInstanceOf(EventConditionNotReadyException.class);
    }

    @Test
    @DisplayName("userMatches — EventConditionNotReadyException")
    void userMatches_throws() {
        assertThatThrownBy(() -> stub.userMatches(SAMPLE_NODE, 1L, CTX))
                .isInstanceOf(EventConditionNotReadyException.class);
    }
}
