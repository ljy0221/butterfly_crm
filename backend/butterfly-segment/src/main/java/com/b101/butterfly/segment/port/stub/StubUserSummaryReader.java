package com.b101.butterfly.segment.port.stub;

import com.b101.butterfly.segment.port.UserSummary;
import com.b101.butterfly.segment.port.UserSummaryReader;

import java.util.Collection;
import java.util.Map;

/**
 * event 모듈 미합류 시 stub. 빈 맵 반환 — userId만 응답에 보이고 externalId/email은 null이 됨.
 *
 * <p>{@link com.b101.butterfly.segment.config.StubPortConfig}가
 * {@code @ConditionalOnMissingBean(UserSummaryReader.class)}으로 등록한다. event 모듈이 진짜
 * 어댑터를 등록하면 자동 비활성화된다.
 */
public class StubUserSummaryReader implements UserSummaryReader {

    @Override
    public Map<Long, UserSummary> findByUserIds(Long projectId, Collection<Long> userIds) {
        return Map.of();
    }
}
