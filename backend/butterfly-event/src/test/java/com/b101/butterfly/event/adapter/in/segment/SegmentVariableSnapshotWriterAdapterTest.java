package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentVariableSnapshotWriterAdapterTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks private SegmentVariableSnapshotWriterAdapter adapter;

    @Test
    @DisplayName("EVENT 필터 키를 variables_json과 segment_variable_stats 갱신 SQL로 반영")
    @SuppressWarnings("unchecked")
    void refreshEventFilterVariables() {
        when(jdbcTemplate.update(anyString(), anyMap())).thenReturn(1);
        GroupNode tree = new GroupNode(
                LogicOp.OR,
                List.of(
                        event("plan", "premium"),
                        event("productId", "SKU-007")));

        adapter.refresh(990002L, 990005L, tree);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbcTemplate, times(6)).update(sqlCaptor.capture(), paramsCaptor.capture());

        String initializeSql = sqlCaptor.getAllValues().get(0);
        assertThat(initializeSql)
                .contains("UPDATE segment_members")
                .contains("variables_json = JSON_OBJECT()")
                .contains("WHERE segment_id = :segmentId");

        String eventSnapshotSql = sqlCaptor.getAllValues().get(1);
        Map<String, Object> eventSnapshotParams = paramsCaptor.getAllValues().get(1);
        assertThat(eventSnapshotSql)
                .contains("JOIN events ev FORCE INDEX (idx_events_segment_filter_lookup)")
                .contains("JSON_SET")
                .contains("JSON_UNQUOTE(JSON_EXTRACT(ev.event_data, :jsonPath))")
                .contains("WHERE sm.segment_id = :segmentId")
                .contains("AND v.variable_value IS NOT NULL");
        assertThat(eventSnapshotParams)
                .containsEntry("projectId", 990002L)
                .containsEntry("segmentId", 990005L)
                .containsEntry("categoryId", 990002L)
                .containsEntry("jsonPath", "$.\"plan\"")
                .containsEntry("eventFilterPath0", "$.\"plan\"")
                .containsEntry("eventFilterVal0", "premium");

        assertThat(sqlCaptor.getAllValues().get(3))
                .contains("DELETE FROM segment_variable_stats");
        assertThat(sqlCaptor.getAllValues().get(4))
                .contains("INSERT INTO segment_variable_stats")
                .contains(":sourceType");
        assertThat(paramsCaptor.getAllValues().get(4))
                .containsEntry("variableKey", "plan")
                .containsEntry("sourceType", "EVENT_CONTEXT")
                .containsEntry("jsonPath", "$.\"plan\"");
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
}
