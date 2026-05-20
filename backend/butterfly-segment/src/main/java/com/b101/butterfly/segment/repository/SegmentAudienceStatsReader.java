package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.dto.response.SegmentAudienceStatsResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SegmentAudienceStatsReader {

    private static final String PLACEHOLDER_EXTERNAL_ID = "__anonymous__";

    private static final String SQL =
            """
            SELECT
                sm.segment_id,
                COUNT(DISTINCT sm.user_id) AS total_count,
                COUNT(DISTINCT CASE
                    WHEN u.push_opt_in = true THEN sm.user_id
                END) AS push_opt_in_count,
                COUNT(DISTINCT CASE
                    WHEN u.push_opt_in = true
                     AND u.external_id <> :placeholder
                     AND d.user_id IS NOT NULL THEN sm.user_id
                END) AS push_sendable_count
            FROM segment_members sm
            LEFT JOIN users u
              ON u.user_id = sm.user_id
             AND u.project_id = :projectId
            LEFT JOIN devices d
              ON d.user_id = u.user_id
             AND d.project_id = :projectId
             AND d.token_valid = true
            WHERE sm.segment_id IN (:segmentIds)
              AND (sm.expires_at IS NULL OR sm.expires_at > :now)
            GROUP BY sm.segment_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Map<Long, SegmentAudienceStatsResponse> countBySegmentIds(
            Long projectId,
            List<Long> segmentIds
    ) {
        if (projectId == null || segmentIds == null || segmentIds.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("segmentIds", segmentIds)
                .addValue("now", LocalDateTime.now())
                .addValue("placeholder", PLACEHOLDER_EXTERNAL_ID);

        Map<Long, SegmentAudienceStatsResponse> result = new HashMap<>();
        jdbcTemplate.query(SQL, params, rs -> {
            result.put(
                    rs.getLong("segment_id"),
                    new SegmentAudienceStatsResponse(
                            rs.getLong("total_count"),
                            rs.getLong("push_opt_in_count"),
                            rs.getLong("push_sendable_count")));
        });
        return result;
    }
}
