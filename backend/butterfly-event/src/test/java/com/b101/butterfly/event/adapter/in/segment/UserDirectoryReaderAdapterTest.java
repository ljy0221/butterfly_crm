package com.b101.butterfly.event.adapter.in.segment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
}
