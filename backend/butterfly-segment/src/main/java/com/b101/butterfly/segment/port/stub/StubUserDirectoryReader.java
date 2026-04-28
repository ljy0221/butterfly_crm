package com.b101.butterfly.segment.port.stub;

import com.b101.butterfly.segment.port.UserDirectoryReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * customer 모듈 미합류 시 자동 등록되는 stub. 항상 빈 결과 — 미리보기 estimatedCount=0.
 */
@Component
@ConditionalOnMissingBean(UserDirectoryReader.class)
public class StubUserDirectoryReader implements UserDirectoryReader {

    @Override
    public List<Long> queryUserIds(String sql, Map<String, Object> params) {
        return List.of();
    }
}
