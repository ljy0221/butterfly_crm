package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.segment.entity.converter.VariablesJsonConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.Arrays;
import java.util.List;

/**
 * {@link SegmentMemberRepositoryCustom} 구현 — JdbcTemplate batchUpdate 기반.
 *
 * <p>한 statement 송신으로 N 건 INSERT 를 처리해 JPA merge 경로 우회.
 * 빌드 직전 {@code deleteBySegmentId} 가 동일 segment_id 의 기존 row 를 모두 비웠으므로
 * PK 충돌 가능성은 없다. 그래도 안전망으로 {@code INSERT IGNORE} 를 사용해 race 시
 * 중복키 예외로 chunk 전체가 실패하는 것을 막는다 (현 흐름에서는 발생 안 함).
 */
@RequiredArgsConstructor
public class SegmentMemberRepositoryImpl implements SegmentMemberRepositoryCustom {

    private static final String SQL =
            "INSERT IGNORE INTO segment_members "
                    + "(segment_id, user_id, matched_at, expires_at, variables_json, variables_captured_at) "
                    + "VALUES (:segmentId, :userId, :matchedAt, :expiresAt, "
                    + ":variablesJson, :variablesCapturedAt)";

    private static final VariablesJsonConverter VARIABLES_CONVERTER =
            new VariablesJsonConverter();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public int bulkInsert(List<SegmentMember> members) {
        if (members.isEmpty()) {
            return 0;
        }
        SqlParameterSource[] params =
                members.stream()
                        .map(SegmentMemberRepositoryImpl::toParams)
                        .toArray(SqlParameterSource[]::new);
        int[] updated = jdbcTemplate.batchUpdate(SQL, params);
        return Arrays.stream(updated).sum();
    }

    private static SqlParameterSource toParams(SegmentMember m) {
        return new MapSqlParameterSource()
                .addValue("segmentId", m.getSegmentId())
                .addValue("userId", m.getUserId())
                .addValue("matchedAt", m.getMatchedAt())
                .addValue("expiresAt", m.getExpiresAt())
                .addValue("variablesJson", VARIABLES_CONVERTER.convertToDatabaseColumn(m.getVariables()))
                .addValue("variablesCapturedAt", m.getVariablesCapturedAt());
    }
}
