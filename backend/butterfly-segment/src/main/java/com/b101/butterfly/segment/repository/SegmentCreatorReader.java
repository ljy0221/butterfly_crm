package com.b101.butterfly.segment.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SegmentCreatorReader {

    private static final String SQL =
            """
            SELECT member_id, name
            FROM members
            WHERE member_id IN (:memberIds)
              AND is_deleted = false
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Map<Long, String> findNamesByMemberIds(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("memberIds", memberIds);

        Map<Long, String> result = new HashMap<>();
        jdbcTemplate.query(SQL, params, rs -> {
            result.put(rs.getLong("member_id"), rs.getString("name"));
        });
        return result;
    }
}
