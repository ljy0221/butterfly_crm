package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.segment.port.UserSummary;
import com.b101.butterfly.segment.port.UserSummaryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 8-1 ([-119]) — segment 대상 유저 목록 API용 user 요약 정보 일괄 조회.
 *
 * <p>흐름:
 * <ul>
 *   <li>비어있는 userIds → 빈 맵</li>
 *   <li>{@code WHERE project_id = :projectId AND user_id IN (:ids) AND external_id <> '__anonymous__'}
 *       — Codex 합의: project 경계 강제 + ADR-0012 placeholder user 제외 (v1 known-user only)</li>
 *   <li>{@code last_active_at}는 ERD에 없으므로 v1에서 항상 null. v1.5/v2 후속 task로 정밀화.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class UserSummaryReaderAdapter implements UserSummaryReader {

    private static final String SQL =
            "SELECT user_id, external_id, email "
                    + "FROM users "
                    + "WHERE project_id = :projectId "
                    + "  AND user_id IN (:ids) "
                    + "  AND external_id <> :placeholder";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, UserSummary> findByUserIds(Long projectId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("ids", userIds);
        params.put("placeholder", User.PLACEHOLDER_EXTERNAL_ID);

        Map<Long, UserSummary> result = new HashMap<>();
        jdbcTemplate.query(
                SQL,
                params,
                (rs, rowNum) -> {
                    long userId = rs.getLong("user_id");
                    UserSummary summary =
                            new UserSummary(
                                    userId,
                                    rs.getString("external_id"),
                                    rs.getString("email"),
                                    null);
                    result.put(userId, summary);
                    return null;
                });
        return result;
    }
}
