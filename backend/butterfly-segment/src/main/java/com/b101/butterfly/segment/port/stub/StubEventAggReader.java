package com.b101.butterfly.segment.port.stub;

import com.b101.butterfly.segment.port.EventAggReader;

import java.util.List;

/**
 * event 모듈 미합류 시 stub. EVENT 조건은 Phase 6 합류 전까지 미지원.
 *
 * <p>{@link com.b101.butterfly.segment.config.StubPortConfig}가 {@code @ConditionalOnMissingBean}으로
 * 등록한다.
 */
public class StubEventAggReader implements EventAggReader {

    @Override
    public List<Long> queryMatchingUserIds(Object eventCondition) {
        throw new UnsupportedOperationException(
                "EVENT 조건은 event 모듈 합류(Phase 6) 전까지 평가할 수 없습니다.");
    }
}
