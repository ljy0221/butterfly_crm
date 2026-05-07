package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.port.EventQueryContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAggAdapterTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks private EventAggAdapter adapter;

    private static final Long PROJECT_ID = 100L;
    private static final Long CATEGORY_ID = 7L;
    private static final LocalDate AS_OF = LocalDate.of(2026, 4, 30);
    private static final EventQueryContext CONTEXT =
            new EventQueryContext(PROJECT_ID, AS_OF, ZoneId.of("Asia/Seoul"));

    @SuppressWarnings("unchecked")
    private void stubQueryReturning(List<Long> userIds) {
        when(jdbcTemplate.queryForStream(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(userIds.stream());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Period.Relative: WHERE log_date >= asOfDate.minusDays(days), HAVING SUM >= minCount")
    void relativePeriodBuildsCorrectSqlAndParams() {
        stubQueryReturning(List.of(1L, 2L));

        EventNode node = new EventNode(CATEGORY_ID, List.of(), new Period.Relative(7), 5);

        Stream<Long> result = adapter.queryMatchingUserIds(node, CONTEXT);
        assertThat(result.toList()).containsExactly(1L, 2L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbcTemplate)
                .queryForStream(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        Map<String, Object> params = paramsCaptor.getValue();

        assertThat(sql).contains("AND log_date >= :fromDate");
        assertThat(sql).contains("HAVING SUM(event_count) >= :minCount");
        assertThat(sql).contains("GROUP BY user_id");

        assertThat(params)
                .containsEntry("projectId", PROJECT_ID)
                .containsEntry("categoryId", CATEGORY_ID)
                .containsEntry("minCount", 5)
                .containsEntry("fromDate", AS_OF.minusDays(7));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Period.Absolute: WHERE log_date BETWEEN startDate AND endDate")
    void absolutePeriodBuildsBetweenClause() {
        stubQueryReturning(List.of());

        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 30);
        EventNode node =
                new EventNode(CATEGORY_ID, List.of(), new Period.Absolute(start, end), 1);

        adapter.queryMatchingUserIds(node, CONTEXT);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbcTemplate)
                .queryForStream(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        assertThat(sqlCaptor.getValue()).contains("AND log_date BETWEEN :startDate AND :endDate");
        assertThat(paramsCaptor.getValue())
                .containsEntry("startDate", start)
                .containsEntry("endDate", end);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Period.Cumulative: log_date 조건 없음 (전체 기간)")
    void cumulativePeriodOmitsDateClause() {
        stubQueryReturning(List.of());

        EventNode node = new EventNode(CATEGORY_ID, List.of(), new Period.Cumulative(), 1);

        adapter.queryMatchingUserIds(node, CONTEXT);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbcTemplate)
                .queryForStream(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql).doesNotContain("log_date >=").doesNotContain("BETWEEN");
        assertThat(sql).contains("WHERE project_id = :projectId");
        assertThat(sql).contains("HAVING SUM(event_count) >= :minCount");

        assertThat(paramsCaptor.getValue())
                .containsKeys("projectId", "categoryId", "minCount")
                .doesNotContainKeys("fromDate", "startDate", "endDate");
    }

    @Test
    @DisplayName("userMatches: SUM >= minCount 시 true")
    void userMatchesReturnsTrue() {
        when(jdbcTemplate.queryForObject(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(5L);

        EventNode node = new EventNode(CATEGORY_ID, List.of(), new Period.Cumulative(), 3);

        assertThat(adapter.userMatches(node, 42L, CONTEXT)).isTrue();
    }

    @Test
    @DisplayName("userMatches: SUM < minCount 시 false")
    void userMatchesReturnsFalse() {
        when(jdbcTemplate.queryForObject(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(2L);

        EventNode node = new EventNode(CATEGORY_ID, List.of(), new Period.Cumulative(), 3);

        assertThat(adapter.userMatches(node, 42L, CONTEXT)).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("materializeEventMatchesIntoSegment: INSERT INTO segment_members + 적절한 SQL/params + 리턴 row 수")
    void materializeBuildsInsertSelectStatement() {
        when(jdbcTemplate.update(anyString(), anyMap())).thenReturn(1234);

        EventNode node = new EventNode(CATEGORY_ID, List.of(), new Period.Relative(7), 5);
        long inserted = adapter.materializeEventMatchesIntoSegment(990002L, node, CONTEXT);

        assertThat(inserted).isEqualTo(1234L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getValue();
        Map<String, Object> params = paramsCaptor.getValue();

        assertThat(sql)
                .contains("INSERT INTO segment_members")
                .contains("SELECT :segmentId, user_id, NOW(3), NULL")
                .contains("FROM event_daily_agg")
                .contains("WHERE project_id = :projectId")
                .contains("AND category_id = :categoryId")
                .contains("AND log_date >= :fromDate")
                .contains("GROUP BY user_id")
                .contains("HAVING SUM(event_count) >= :minCount");

        assertThat(params)
                .containsEntry("segmentId", 990002L)
                .containsEntry("projectId", PROJECT_ID)
                .containsEntry("categoryId", CATEGORY_ID)
                .containsEntry("minCount", 5)
                .containsEntry("fromDate", AS_OF.minusDays(7));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("materializeEventMatchesIntoSegment Cumulative — log_date 절 없음")
    void materializeWithCumulativePeriodOmitsDateClause() {
        when(jdbcTemplate.update(anyString(), anyMap())).thenReturn(0);

        EventNode node = new EventNode(CATEGORY_ID, List.of(), new Period.Cumulative(), 1);
        adapter.materializeEventMatchesIntoSegment(42L, node, CONTEXT);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("INSERT INTO segment_members");
        assertThat(sql).doesNotContain("log_date >=").doesNotContain("BETWEEN");

        assertThat(paramsCaptor.getValue())
                .containsKeys("segmentId", "projectId", "categoryId", "minCount")
                .doesNotContainKeys("fromDate", "startDate", "endDate");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("userMatches: WHERE에 user_id 조건 + 적절한 params")
    void userMatchesBuildsCorrectQuery() {
        when(jdbcTemplate.queryForObject(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(0L);

        EventNode node = new EventNode(CATEGORY_ID, List.of(), new Period.Relative(7), 1);

        adapter.userMatches(node, 42L, CONTEXT);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbcTemplate)
                .queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("AND user_id = :userId")
                .contains("AND log_date >= :fromDate");
        assertThat(paramsCaptor.getValue())
                .containsEntry("projectId", PROJECT_ID)
                .containsEntry("categoryId", CATEGORY_ID)
                .containsEntry("userId", 42L)
                .containsEntry("fromDate", AS_OF.minusDays(7));
    }

}
