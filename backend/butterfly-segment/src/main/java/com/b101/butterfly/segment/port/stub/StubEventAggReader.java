package com.b101.butterfly.segment.port.stub;

import com.b101.butterfly.segment.port.EventAggReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * customer 모듈 미합류 시 자동 등록되는 stub. EVENT 조건은 Phase 6 합류 전까지 미지원.
 */
@Component
@ConditionalOnMissingBean(EventAggReader.class)
public class StubEventAggReader implements EventAggReader {

    @Override
    public List<Long> queryMatchingUserIds(Object eventCondition) {
        throw new UnsupportedOperationException(
                "EVENT 조건은 customer 모듈 합류(Phase 6) 전까지 평가할 수 없습니다.");
    }
}
