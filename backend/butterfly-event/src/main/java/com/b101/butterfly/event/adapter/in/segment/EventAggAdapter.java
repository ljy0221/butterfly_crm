package com.b101.butterfly.event.adapter.in.segment;

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
 * <p>schemaVersion 4부터 EventNode.categoryId(Long)를 직접 사용 — name → ID 변환 제거로
 * 매 호출당 EventCategoryRepository 조회 비용 0.
 *
 * <p>흐름:
 * <ol>
 *     <li>Period 분기로 WHERE log_date 절 동적 조립</li>
 *     <li>GROUP BY user_id HAVING SUM(event_count) >= minCount</li>
 * </ol>
 *
 * <p>Stream 반환 — 큰 결과 집합에서 메모리 회피. 호출자는 try-with-resources로 close 책임.
 */
@Component
@RequiredArgsConstructor
public class EventAggAdapter implements EventAggReader {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Stream<Long> queryMatchingUserIds(EventNode node, EventQueryContext context) {
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
        params.put("categoryId", node.categoryId());
        params.put("minCount", node.minCount());

        return jdbcTemplate.queryForStream(sql, params, (rs, rowNum) -> rs.getLong("user_id"));
    }

    /**
     * 단일 사용자 매칭 검사 — Phase 6-3 실시간 매칭. {@code WHERE user_id=:uid} 제약으로 단일 row
     * SUM HAVING 검사. 기본 fallback(전체 스트림 anyMatch)보다 훨씬 효율적.
     */
    @Override
    public boolean userMatches(EventNode node, Long userId, EventQueryContext context) {
        SqlParts parts = sqlPartsFor(node.period(), context);

        String sql = """
                SELECT COALESCE(SUM(event_count), 0) AS total
                FROM event_daily_agg
                WHERE project_id = :projectId
                  AND category_id = :categoryId
                  AND user_id = :userId
                  %s
                """.formatted(parts.dateClause());

        Map<String, Object> params = new HashMap<>(parts.params());
        params.put("projectId", context.projectId());
        params.put("categoryId", node.categoryId());
        params.put("userId", userId);

        Long total =
                jdbcTemplate.queryForObject(
                        sql, params, (rs, rowNum) -> rs.getLong("total"));
        return total != null && total >= node.minCount();
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
