package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.segment.port.UserDirectoryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserDirectoryReaderAdapter implements UserDirectoryReader {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<Long> queryUserIds(String sql, Map<String, Object> params) {
        return jdbcTemplate.queryForList(sql, params, Long.class);
    }

    /**
     * {@inheritDoc}
     *
     * <p>단일 statement: {@code INSERT IGNORE INTO segment_members (...) SELECT ... FROM (selectSql) m}.
     * 두 underscore 로 시작하는 reserved param 이름 ({@code __segId} / {@code __matchedAt}) 은
     * Converter 가 만드는 사용자 정의 param 과 충돌하지 않도록 prefix 분리.
     */
    @Override
    public long materializeUserMatchesIntoSegment(
            Long segmentId, String selectSql, Map<String, Object> params) {
        Map<String, Object> merged = new HashMap<>(params);
        merged.put("__segId", segmentId);
        merged.put("__matchedAt", LocalDateTime.now());

        String wrapped =
                "INSERT IGNORE INTO segment_members (segment_id, user_id, matched_at, expires_at) "
                        + "SELECT :__segId, m.user_id, :__matchedAt, NULL FROM ("
                        + selectSql
                        + ") AS m";

        return jdbcTemplate.update(wrapped, merged);
    }
}
