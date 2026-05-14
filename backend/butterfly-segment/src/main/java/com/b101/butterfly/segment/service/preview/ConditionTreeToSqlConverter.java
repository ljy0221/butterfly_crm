package com.b101.butterfly.segment.service.preview;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.DeviceNode;
import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.UserNode;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.port.EventQueryContext;
import com.b101.butterfly.segment.service.preview.dialect.SqlDialect;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * TAG/USER/DEVICE/EVENT 조건 트리를 named parameter SQL로 변환한다.
 *
 * <p>EVENT 노드는 필터가 없으면 event_daily_agg, 필터가 있으면 raw events.event_data JSON 조건으로 평가한다.
 *
 * <p>USER/DEVICE의 {@code key}는 SQL identifier로 들어가므로 화이트리스트로 강제한다 — 임의 컬럼 노출 차단.
 * TAG의 {@code key}는 행 데이터(`user_tags.tag_key` 컬럼 값)이므로 placeholder로 안전하게 바인딩.
 */
@Component
@RequiredArgsConstructor
public class ConditionTreeToSqlConverter {

    private final SqlDialect dialect;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static final Map<String, String> USER_KEY_TO_COLUMN =
            Map.of(
                    "email", "email",
                    "phone", "phone",
                    "birthDate", "birth_date",
                    "pushOptIn", "push_opt_in",
                    "signedUpAt", "signed_up_at");

    static final Map<String, String> DEVICE_KEY_TO_COLUMN =
            Map.of(
                    "platform", "platform",
                    "appVersion", "app_version",
                    "osVersion", "os_version",
                    "lastActiveAt", "last_active_at",
                    "tokenValid", "token_valid");

    public SqlFragment convert(GroupNode root, Long projectId) {
        return convert(root, new EventQueryContext(projectId, LocalDate.now(SEOUL), SEOUL));
    }

    public SqlFragment convert(GroupNode root, EventQueryContext context) {
        AtomicInteger counter = new AtomicInteger();

        // S14P31B101-269: root 가 OR-of-filtered-EVENT-same-bucket 일 때 outer users 를
        // 거치지 않고 events 한 번 scan + SUM(CASE) 로 결합. dependent EXISTS 패턴 회피.
        if (isMergeableFilteredEventOrGroup(root)) {
            SqlFragment merged = mergedFilteredEventGroupFragment(root, counter, context);
            Map<String, Object> params = new HashMap<>(merged.params());
            params.put("projectId", context.projectId());
            return new SqlFragment(merged.sql(), params);
        }

        SqlFragment body = walk(root, counter, context);

        Map<String, Object> params = new HashMap<>(body.params());
        params.put("projectId", context.projectId());

        String sql =
                "SELECT u.user_id FROM users u WHERE u.project_id = :projectId AND ("
                        + body.sql()
                        + ")";
        return new SqlFragment(sql, params);
    }

    /**
     * root GroupNode 가 single-scan 최적화 대상인지 판정.
     *
     * <p>모두 만족해야 함: logic=OR, conditions≥2, 모두 EventNode, 모두 filter 비어있지 않음,
     * 모두 같은 categoryId, 모두 같은 period. 한 leaf 안의 filter 들은 AND 결합되며 leaf 간은
     * OR 로 묶여 SUM(CASE)..>=minCount 들의 OR HAVING 로 표현 가능.
     */
    private static boolean isMergeableFilteredEventOrGroup(GroupNode root) {
        if (root.logic() != LogicOp.OR) return false;
        List<ConditionNode> children = root.conditions();
        if (children == null || children.size() < 2) return false;

        Long firstCat = null;
        Period firstPeriod = null;
        for (ConditionNode child : children) {
            if (!(child instanceof EventNode e)) return false;
            if (e.filters() == null || e.filters().isEmpty()) return false;
            if (firstCat == null) {
                firstCat = e.categoryId();
                firstPeriod = e.period();
                continue;
            }
            if (!firstCat.equals(e.categoryId())) return false;
            if (!firstPeriod.equals(e.period())) return false;
        }
        return true;
    }

    /**
     * OR-of-filtered-EVENT-same-bucket 트리를 단일 events scan SQL 로 컴파일.
     *
     * <pre>{@code
     * SELECT ev.user_id FROM events ev
     *  WHERE ev.project_id = :projectId AND ev.category_id = :eventCategoryId0 [date]
     *  GROUP BY ev.user_id
     *  HAVING SUM(CASE WHEN <leaf0 filters AND-결합> THEN 1 ELSE 0 END) >= :eventMinCount0
     *      OR SUM(CASE WHEN <leaf1 filters AND-결합> THEN 1 ELSE 0 END) >= :eventMinCount1
     *      ...
     * }</pre>
     */
    private SqlFragment mergedFilteredEventGroupFragment(
            GroupNode root, AtomicInteger counter, EventQueryContext context) {
        EventNode firstLeaf = (EventNode) root.conditions().get(0);
        int sharedIdx = counter.getAndIncrement();
        String catParam = "eventCategoryId" + sharedIdx;
        SqlFragment dateFragment = rawEventDateFragment(firstLeaf.period(), sharedIdx, context);

        Map<String, Object> params = new HashMap<>(dateFragment.params());
        params.put(catParam, firstLeaf.categoryId());

        List<String> havingClauses = new ArrayList<>();
        for (int leafIdx = 0; leafIdx < root.conditions().size(); leafIdx++) {
            EventNode leaf = (EventNode) root.conditions().get(leafIdx);
            List<String> filterExprs = new ArrayList<>();
            for (int f = 0; f < leaf.filters().size(); f++) {
                SqlFragment ff = eventFilterFragment(leaf.filters().get(f), leafIdx, f);
                // eventFilterFragment 가 " AND <expr>" 형태로 반환 — leading " AND " 제거.
                String exprOnly = ff.sql().startsWith(" AND ") ? ff.sql().substring(5) : ff.sql();
                filterExprs.add(exprOnly);
                params.putAll(ff.params());
            }
            String allFilters = String.join(" AND ", filterExprs);
            String minCountParam = "eventMinCount" + leafIdx;
            havingClauses.add(
                    "SUM(CASE WHEN " + allFilters + " THEN 1 ELSE 0 END) >= :" + minCountParam);
            params.put(minCountParam, leaf.minCount());
        }

        String sql =
                "SELECT ev.user_id FROM events ev"
                        + " WHERE ev.project_id = :projectId"
                        + " AND ev.category_id = :" + catParam
                        + dateFragment.sql()
                        + " GROUP BY ev.user_id"
                        + " HAVING " + String.join(" OR ", havingClauses);
        return new SqlFragment(sql, params);
    }

    private SqlFragment walk(ConditionNode node, AtomicInteger counter, EventQueryContext context) {
        return switch (node) {
            case GroupNode g -> walkGroup(g, counter, context);
            case TagNode t -> tagFragment(t, counter);
            case UserNode u -> userFragment(u, counter);
            case DeviceNode d -> deviceFragment(d, counter);
            case EventNode e -> eventFragment(e, counter, context);
        };
    }

    private SqlFragment walkGroup(
            GroupNode group, AtomicInteger counter, EventQueryContext context) {
        List<SqlFragment> fragments =
                group.conditions().stream().map(child -> walk(child, counter, context)).toList();
        String joiner = group.logic() == LogicOp.AND ? " AND " : " OR ";
        String joined =
                "(" + fragments.stream().map(SqlFragment::sql).collect(Collectors.joining(joiner))
                        + ")";
        Map<String, Object> params = new HashMap<>();
        fragments.forEach(f -> params.putAll(f.params()));
        return new SqlFragment(joined, params);
    }

    private SqlFragment tagFragment(TagNode tag, AtomicInteger counter) {
        int idx = counter.getAndIncrement();
        String keyParam = "tagKey" + idx;
        String valParam = "tagVal" + idx;

        String opClause = opClause("ut.tag_value", tag.op(), valParam);
        String sql =
                "EXISTS (SELECT 1 FROM user_tags ut WHERE ut.user_id = u.user_id"
                        + " AND ut.project_id = :projectId"
                        + " AND ut.tag_key = :" + keyParam
                        + " AND " + opClause + ")";

        Map<String, Object> params = new HashMap<>();
        params.put(keyParam, tag.key());
        params.put(valParam, bindValue(tag.op(), tag.value()));
        return new SqlFragment(sql, params);
    }

    private SqlFragment userFragment(UserNode node, AtomicInteger counter) {
        String column = requireKnownKey(USER_KEY_TO_COLUMN, node.key(), "user");
        int idx = counter.getAndIncrement();
        String valParam = "userVal" + idx;
        String opClause = opClause("u." + column, node.op(), valParam);
        return new SqlFragment(
                opClause, Map.of(valParam, bindValue(node.op(), node.value())));
    }

    private SqlFragment deviceFragment(DeviceNode node, AtomicInteger counter) {
        String column = requireKnownKey(DEVICE_KEY_TO_COLUMN, node.key(), "device");
        int idx = counter.getAndIncrement();
        String valParam = "deviceVal" + idx;
        String opClause = opClause("d." + column, node.op(), valParam);
        String sql =
                "EXISTS (SELECT 1 FROM devices d WHERE d.user_id = u.user_id AND " + opClause + ")";
        return new SqlFragment(sql, Map.of(valParam, bindValue(node.op(), node.value())));
    }

    private SqlFragment eventFragment(
            EventNode node, AtomicInteger counter, EventQueryContext context) {
        if (node.filters() != null && !node.filters().isEmpty()) {
            return rawEventFragment(node, counter, context);
        }
        return eventDailyAggFragment(node, counter, context);
    }

    private SqlFragment eventDailyAggFragment(
            EventNode node, AtomicInteger counter, EventQueryContext context) {
        int idx = counter.getAndIncrement();
        String categoryParam = "eventCategoryId" + idx;
        String minCountParam = "eventMinCount" + idx;
        SqlFragment dateFragment = eventDailyAggDateFragment(node.period(), idx, context);

        String sql =
                "EXISTS (SELECT 1 FROM event_daily_agg e WHERE e.user_id = u.user_id"
                        + " AND e.project_id = :projectId"
                        + " AND e.category_id = :" + categoryParam
                        + dateFragment.sql()
                        + " GROUP BY e.user_id"
                        + " HAVING SUM(e.event_count) >= :" + minCountParam
                        + ")";

        Map<String, Object> params = new HashMap<>(dateFragment.params());
        params.put(categoryParam, node.categoryId());
        params.put(minCountParam, node.minCount());
        return new SqlFragment(sql, params);
    }

    private SqlFragment rawEventFragment(
            EventNode node, AtomicInteger counter, EventQueryContext context) {
        int idx = counter.getAndIncrement();
        String categoryParam = "eventCategoryId" + idx;
        String minCountParam = "eventMinCount" + idx;
        SqlFragment dateFragment = rawEventDateFragment(node.period(), idx, context);

        List<SqlFragment> filterFragments = new ArrayList<>();
        for (int i = 0; i < node.filters().size(); i++) {
            filterFragments.add(eventFilterFragment(node.filters().get(i), idx, i));
        }

        String filterSql = filterFragments.stream()
                .map(SqlFragment::sql)
                .collect(Collectors.joining(""));
        String sql =
                "EXISTS (SELECT 1 FROM events ev WHERE ev.user_id = u.user_id"
                        + " AND ev.project_id = :projectId"
                        + " AND ev.category_id = :" + categoryParam
                        + dateFragment.sql()
                        + filterSql
                        + " GROUP BY ev.user_id"
                        + " HAVING COUNT(*) >= :" + minCountParam
                        + ")";

        Map<String, Object> params = new HashMap<>(dateFragment.params());
        filterFragments.forEach(f -> params.putAll(f.params()));
        params.put(categoryParam, node.categoryId());
        params.put(minCountParam, node.minCount());
        return new SqlFragment(sql, params);
    }

    private static SqlFragment eventDailyAggDateFragment(
            Period period, int idx, EventQueryContext context) {
        return switch (period) {
            case Period.Relative r -> {
                String fromParam = "eventFromDate" + idx;
                yield new SqlFragment(
                        " AND e.log_date >= :" + fromParam,
                        Map.of(fromParam, context.asOfDate().minusDays(r.days())));
            }
            case Period.Absolute a -> {
                String startParam = "eventStartDate" + idx;
                String endParam = "eventEndDate" + idx;
                yield new SqlFragment(
                        " AND e.log_date BETWEEN :" + startParam + " AND :" + endParam,
                        Map.of(startParam, a.startDate(), endParam, a.endDate()));
            }
            case Period.Cumulative ignored -> new SqlFragment("", Map.of());
        };
    }

    private static SqlFragment rawEventDateFragment(
            Period period, int idx, EventQueryContext context) {
        return switch (period) {
            case Period.Relative r -> {
                String fromParam = "eventFromDate" + idx;
                yield new SqlFragment(
                        " AND ev.occurred_at >= :" + fromParam,
                        Map.of(fromParam, context.asOfDate().minusDays(r.days())));
            }
            case Period.Absolute a -> {
                String startParam = "eventStartDate" + idx;
                String endParam = "eventEndExclusiveDate" + idx;
                yield new SqlFragment(
                        " AND ev.occurred_at >= :" + startParam
                                + " AND ev.occurred_at < :" + endParam,
                        Map.of(startParam, a.startDate(), endParam, a.endDate().plusDays(1)));
            }
            case Period.Cumulative ignored -> new SqlFragment("", Map.of());
        };
    }

    private SqlFragment eventFilterFragment(EventFilter filter, int eventIdx, int filterIdx) {
        String valParam = "eventFilterVal" + eventIdx + "_" + filterIdx;
        SqlDialect.PredicateFragment pred =
                dialect.filterPredicate("ev.event_data", filter.key(), filter.op(), valParam);

        Map<String, Object> params = new HashMap<>(pred.extraParams());
        params.put(valParam, eventFilterBindValue(filter.op(), filter.value()));
        return new SqlFragment(" AND " + pred.sql(), params);
    }

    private static String requireKnownKey(
            Map<String, String> whitelist, String key, String nodeType) {
        String column = whitelist.get(key);
        if (column == null) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "Unknown " + nodeType + " key: " + key);
        }
        return column;
    }

    private static String opClause(String columnExpr, Operator op, String paramName) {
        return switch (op) {
            case EQ -> columnExpr + " = :" + paramName;
            case NEQ -> columnExpr + " <> :" + paramName;
            case GT -> columnExpr + " > :" + paramName;
            case LT -> columnExpr + " < :" + paramName;
            case GTE -> columnExpr + " >= :" + paramName;
            case LTE -> columnExpr + " <= :" + paramName;
            case IN -> columnExpr + " IN (:" + paramName + ")";
            case CONTAINS -> columnExpr + " LIKE :" + paramName;
        };
    }

    private static Object bindValue(Operator op, JsonNode value) {
        if (op == Operator.IN) {
            List<Object> list = new ArrayList<>();
            value.forEach(v -> list.add(scalar(v)));
            return list;
        }
        if (op == Operator.CONTAINS) {
            return "%" + value.asText() + "%";
        }
        return scalar(value);
    }

    private static Object eventFilterBindValue(Operator op, JsonNode value) {
        if (op == Operator.IN) {
            List<Object> list = new ArrayList<>();
            value.forEach(v -> list.add(scalarAsText(v)));
            return list;
        }
        if (op == Operator.CONTAINS) {
            return "%" + value.asText() + "%";
        }
        if (op == Operator.GT || op == Operator.LT || op == Operator.GTE || op == Operator.LTE) {
            return scalar(value);
        }
        return scalarAsText(value);
    }

    private static Object scalar(JsonNode value) {
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return value.asText();
    }

    private static String scalarAsText(JsonNode value) {
        return value.asText();
    }

    private static String jsonPath(String key) {
        String escaped = key.replace("\\", "\\\\").replace("\"", "\\\"");
        return "$.\"" + escaped + "\"";
    }
}
