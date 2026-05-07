package com.b101.butterfly.segment.port.stub;

import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.EventQueryContext;
import com.b101.butterfly.segment.service.preview.EventConditionNotReadyException;

import java.util.stream.Stream;

/**
 * event 모듈 미합류 시 stub. EVENT 조건은 평가 불가.
 *
 * <p>{@link com.b101.butterfly.segment.config.StubPortConfig}가 {@code @ConditionalOnMissingBean}으로
 * 등록한다. event 모듈이 진짜 어댑터를 등록하면 본 stub은 자동 비활성화된다.
 *
 * <p>호출 시 {@link EventConditionNotReadyException}을 던져 PreviewService가
 * SEGMENT_501_EVENT_NOT_READY로 매핑하도록 한다.
 */
public class StubEventAggReader implements EventAggReader {

    @Override
    public Stream<Long> queryMatchingUserIds(EventNode node, EventQueryContext context) {
        throw new EventConditionNotReadyException();
    }

    @Override
    public boolean userMatches(EventNode node, Long userId, EventQueryContext context) {
        throw new EventConditionNotReadyException();
    }

    @Override
    public long materializeEventMatchesIntoSegment(
            Long segmentId, EventNode node, EventQueryContext context) {
        throw new EventConditionNotReadyException();
    }
}
