package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.event.config.ClickHouseConfig.ClickHouseAccess;
import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickHouseSegmentVariableSnapshotWriterAdapterTest {

    @Mock private DataSource clickHouseDataSource;
    @Mock private NamedParameterJdbcTemplate clickHouseJdbcTemplate;
    @Mock private NamedParameterJdbcTemplate mysqlJdbcTemplate;

    private ClickHouseSegmentVariableSnapshotWriterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ClickHouseSegmentVariableSnapshotWriterAdapter(
                new ClickHouseAccess(clickHouseDataSource, clickHouseJdbcTemplate),
                mysqlJdbcTemplate);
    }

    @Test
    @DisplayName("ClickHouse 활성 시 EVENT 변수는 ClickHouse에서 읽고 MySQL segment_members 통계를 갱신한다")
    @SuppressWarnings("unchecked")
    void refreshEventFilterVariablesFromClickHouse() {
        when(mysqlJdbcTemplate.update(anyString(), anyMap())).thenReturn(1);
        when(clickHouseJdbcTemplate.queryForStream(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(Stream.empty());
        GroupNode tree = new GroupNode(
                LogicOp.OR,
                List.of(event("plan", "premium")));

        adapter.refresh(990002L, 990005L, tree);

        ArgumentCaptor<String> clickHouseSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> clickHouseParamsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(clickHouseJdbcTemplate)
                .queryForStream(
                        clickHouseSqlCaptor.capture(),
                        clickHouseParamsCaptor.capture(),
                        any(RowMapper.class));

        assertThat(clickHouseSqlCaptor.getValue())
                .contains("SELECT user_id, anyLast(JSONExtractString(event_data, 'plan'))")
                .contains("FROM events")
                .contains("WHERE project_id = :projectId")
                .contains("AND category_id = :categoryId")
                .contains("JSONExtractString(event_data, 'plan') = :eventFilterVal0")
                .contains("GROUP BY user_id");
        assertThat(clickHouseParamsCaptor.getValue())
                .containsEntry("projectId", 990002L)
                .containsEntry("categoryId", 990002L)
                .containsEntry("eventFilterVal0", "premium");

        ArgumentCaptor<String> mysqlSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mysqlJdbcTemplate, times(5)).update(mysqlSqlCaptor.capture(), anyMap());
        assertThat(mysqlSqlCaptor.getAllValues().get(0))
                .contains("variables_json = JSON_OBJECT()");
        assertThat(mysqlSqlCaptor.getAllValues().get(1))
                .contains("UPDATE segment_members sm")
                .contains("JOIN segment_variable_snapshot_stage st")
                .contains("JSON_SET")
                .contains("st.variable_value");
        assertThat(mysqlSqlCaptor.getAllValues().get(2))
                .contains("DELETE FROM segment_variable_snapshot_stage");
        assertThat(mysqlSqlCaptor.getAllValues().get(3))
                .contains("DELETE FROM segment_variable_stats");
        assertThat(mysqlSqlCaptor.getAllValues().get(4))
                .contains("INSERT INTO segment_variable_stats")
                .contains(":sourceType");
        verify(mysqlJdbcTemplate, never()).batchUpdate(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class));
    }

    @Test
    @DisplayName("ClickHouse 조회 결과가 있으면 stage table에 적재한 뒤 join update한다")
    @SuppressWarnings("unchecked")
    void stageClickHouseRowsBeforeJoinUpdate() {
        when(mysqlJdbcTemplate.update(anyString(), anyMap())).thenReturn(1);
        when(clickHouseJdbcTemplate.queryForStream(anyString(), anyMap(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<Object> mapper = invocation.getArgument(2);
                    return Stream.of(
                            mapper.mapRow(resultSet(1L, "SKU-001"), 0),
                            mapper.mapRow(resultSet(2L, "SKU-002"), 1));
                });
        GroupNode tree = new GroupNode(
                LogicOp.OR,
                List.of(event("productId", "SKU-001")));

        adapter.refresh(990002L, 990005L, tree);

        ArgumentCaptor<String> batchSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mysqlJdbcTemplate, atLeastOnce())
                .batchUpdate(
                        batchSqlCaptor.capture(),
                        any(org.springframework.jdbc.core.namedparam.SqlParameterSource[].class));
        assertThat(batchSqlCaptor.getValue())
                .contains("INSERT INTO segment_variable_snapshot_stage")
                .contains("snapshot_run_id")
                .contains("ON DUPLICATE KEY UPDATE");
    }

    private static EventNode event(String key, String value) {
        return new EventNode(
                990002L,
                List.of(new EventFilter(
                        key,
                        Operator.EQ,
                        JsonNodeFactory.instance.textNode(value))),
                new Period.Cumulative(),
                1);
    }

    private static ResultSet resultSet(Long userId, String variableValue) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("user_id")).thenReturn(userId);
        when(resultSet.getString("variable_value")).thenReturn(variableValue);
        return resultSet;
    }
}
