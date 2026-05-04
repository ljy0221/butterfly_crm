package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.EventCategoryType;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.global.error.BusinessException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAggAdapterTest {

    @Mock private EventCategoryRepository categoryRepository;
    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks private EventAggAdapter adapter;

    private static final Long PROJECT_ID = 100L;
    private static final Long CATEGORY_ID = 7L;
    private static final String CATEGORY_NAME = "page_view";
    private static final LocalDate AS_OF = LocalDate.of(2026, 4, 30);
    private static final EventQueryContext CONTEXT =
            new EventQueryContext(PROJECT_ID, AS_OF, ZoneId.of("Asia/Seoul"));

    @SuppressWarnings("unchecked")
    private void stubQueryReturning(List<Long> userIds) {
        when(jdbcTemplate.queryForStream(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(userIds.stream());
    }

    private EventCategory category() {
        EventCategory c = EventCategory.builder()
                .projectId(PROJECT_ID)
                .name(CATEGORY_NAME)
                .type(EventCategoryType.CUSTOM)
                .build();
        ReflectionTestUtils.setField(c, "id", CATEGORY_ID);
        return c;
    }

    @Test
    @DisplayName("category 미존재 시 EventErrorCode.CATEGORY_NOT_FOUND")
    void categoryNotFoundThrows() {
        when(categoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.empty());

        EventNode node = new EventNode(CATEGORY_NAME, List.of(), new Period.Cumulative(), 1);

        assertThatThrownBy(() -> adapter.queryMatchingUserIds(node, CONTEXT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.CATEGORY_NOT_FOUND);

        verify(jdbcTemplate, never()).queryForStream(anyString(), anyMap(), any(RowMapper.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Period.Relative: WHERE log_date >= asOfDate.minusDays(days), HAVING SUM >= minCount")
    void relativePeriodBuildsCorrectSqlAndParams() {
        when(categoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        stubQueryReturning(List.of(1L, 2L));

        EventNode node = new EventNode(CATEGORY_NAME, List.of(), new Period.Relative(7), 5);

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
        when(categoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        stubQueryReturning(List.of());

        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 30);
        EventNode node = new EventNode(CATEGORY_NAME, List.of(), new Period.Absolute(start, end), 1);

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
        when(categoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        stubQueryReturning(List.of());

        EventNode node = new EventNode(CATEGORY_NAME, List.of(), new Period.Cumulative(), 1);

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
}
