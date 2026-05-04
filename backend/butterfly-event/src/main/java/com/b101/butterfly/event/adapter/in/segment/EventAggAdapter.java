package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.EventQueryContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * EventAggReader 구현체. event_daily_agg를 조회해 EVENT 조건에 매칭되는 user_id를 반환.
 *
 * <p>흐름:
 * <ol>
 *     <li>category(name) → categoryId(Long) 변환 (event_categories 조회)</li>
 *     <li>Period 분기로 WHERE log_date 절 동적 조립</li>
 *     <li>GROUP BY user_id HAVING SUM(event_count) >= minCount</li>
 * </ol>
 *
 * <p>Stream 반환 — 큰 결과 집합에서 메모리 회피. 호출자는 try-with-resources로 close 책임.
 */
@Component
@RequiredArgsConstructor
public class EventAggAdapter implements EventAggReader {

    private final EventCategoryRepository categoryRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Stream<Long> queryMatchingUserIds(EventNode node, EventQueryContext context) {
        Long categoryId = categoryRepository
                .findByProjectIdAndName(context.projectId(), node.category())
                .map(EventCategory::getId)
                .orElseThrow(() -> new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));

        SqlParts parts = sqlPartsFor(node.period(), context);

        String sql = """
                SELECT user_id
                FROM event_daily_agg
                WHERE project_id = :projectId
                  AND category_id = :categoryId
                  %s
                GROUP BY user_id
                HAVING SUM(event_count) >= :minCount
                """.formatted(parts.dateClause());

        Map<String, Object> params = new HashMap<>(parts.params());
        params.put("projectId", context.projectId());
        params.put("categoryId", categoryId);
        params.put("minCount", node.minCount());

        return jdbcTemplate.queryForStream(sql, params, (rs, rowNum) -> rs.getLong("user_id"));
    }

    private static SqlParts sqlPartsFor(Period period, EventQueryContext context) {
        return switch (period) {
            case Period.Relative r -> new SqlParts(
                    "AND log_date >= :fromDate",
                    Map.of("fromDate", context.asOfDate().minusDays(r.days())));
            case Period.Absolute a -> new SqlParts(
                    "AND log_date BETWEEN :startDate AND :endDate",
                    Map.of("startDate", a.startDate(), "endDate", a.endDate()));
            case Period.Cumulative ignored -> new SqlParts("", Map.of());
        };
    }

    private record SqlParts(String dateClause, Map<String, Object> params) {
    }
}
