package com.b101.butterfly.segment.port.stub;

import com.b101.butterfly.segment.port.UserDirectoryReader;

import java.util.List;
import java.util.Map;

/**
 * event 모듈 미합류 시 stub. 항상 빈 결과 — 미리보기 estimatedCount=0.
 *
 * <p>{@link com.b101.butterfly.segment.config.StubPortConfig}가 {@code @ConditionalOnMissingBean}으로
 * 등록한다 — event 모듈이 진짜 빈을 등록하면 본 stub은 자동 비활성화된다.
 */
public class StubUserDirectoryReader implements UserDirectoryReader {

    @Override
    public List<Long> queryUserIds(String sql, Map<String, Object> params) {
        return List.of();
    }

    @Override
    public long countUserMatches(String selectSql, Map<String, Object> params) {
        return 0L;
    }

    @Override
    public long materializeUserMatchesIntoSegment(
            Long segmentId, String selectSql, Map<String, Object> params) {
        return 0L;
    }
}
