package com.b101.butterfly.event.adapter.in.segment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDirectoryReaderAdapterTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks private UserDirectoryReaderAdapter adapter;

    @Test
    @DisplayName("queryUserIds — JdbcTemplate.queryForList(sql, params, Long.class)에 그대로 위임")
    void queryUserIdsDelegatesToJdbcTemplate() {
        String sql = "SELECT u.user_id FROM users u WHERE u.project_id = :projectId";
        Map<String, Object> params = Map.of("projectId", 100L);
        List<Long> expected = List.of(1L, 2L, 3L);

        when(jdbcTemplate.queryForList(eq(sql), eq(params), eq(Long.class)))
                .thenReturn(expected);

        List<Long> result = adapter.queryUserIds(sql, params);

        assertThat(result).isEqualTo(expected);
        verify(jdbcTemplate).queryForList(sql, params, Long.class);
    }

    @Test
    @DisplayName("queryUserIds — 결과가 비어있어도 빈 리스트 반환 (예외 던지지 않음)")
    void queryUserIdsReturnsEmptyListWhenNoMatch() {
        String sql = "SELECT u.user_id FROM users u WHERE u.project_id = :projectId";
        Map<String, Object> params = Map.of("projectId", 999L);

        when(jdbcTemplate.queryForList(eq(sql), eq(params), eq(Long.class)))
                .thenReturn(List.of());

        List<Long> result = adapter.queryUserIds(sql, params);

        assertThat(result).isEmpty();
    }

    // ---------- S14P31B101-269: materializeUserMatchesIntoSegment ----------

    @Test
    @DisplayName("materializeUserMatchesIntoSegment — INSERT IGNORE ... SELECT 로 wrapping + __segId/__matchedAt 주입")
    @SuppressWarnings("unchecked")
    void materializeWrapsAndInjectsReservedParams() {
        String selectSql = "SELECT ev.user_id FROM events ev WHERE ev.project_id = :projectId";
        Map<String, Object> params = new HashMap<>(Map.of("projectId", 100L));
        when(jdbcTemplate.update(anyString(), anyMap())).thenReturn(1234);

        long inserted = adapter.materializeUserMatchesIntoSegment(7L, selectSql, params);

        assertThat(inserted).isEqualTo(1234L);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(jdbcTemplate).update(sqlCap.capture(), paramsCap.capture());

        // INSERT IGNORE 로 PK 충돌 안전망 + 원 SELECT 를 derived table 로 감쌈.
        assertThat(sqlCap.getValue())
                .startsWith(
                        "INSERT IGNORE INTO segment_members (segment_id, user_id, matched_at, expires_at) SELECT :__segId, m.user_id, :__matchedAt, NULL FROM (")
                .contains(selectSql)
                .endsWith(") AS m");

        // 원 params 유지 + reserved 두 개 주입.
        Map<String, Object> merged = paramsCap.getValue();
        assertThat(merged).containsEntry("projectId", 100L);
        assertThat(merged).containsEntry("__segId", 7L);
        assertThat(merged).containsKey("__matchedAt");
    }
}
