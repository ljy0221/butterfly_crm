package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.port.SegmentVariableSnapshotWriter;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "butterfly.clickhouse.enabled", havingValue = "false", matchIfMissing = true)
public class SegmentVariableSnapshotWriterAdapter implements SegmentVariableSnapshotWriter {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Pattern TEMPLATE_VARIABLE_KEY =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void refresh(Long projectId, Long segmentId, GroupNode conditionTree) {
        VariableSpecs specs = collectVariableSpecs(conditionTree);
        if (specs.isEmpty()) {
            return;
        }

        LocalDateTime capturedAt = LocalDateTime.now();
        initializeMemberSnapshots(segmentId, capturedAt);
        for (TagVariableSpec spec : specs.tagSpecs()) {
            refreshTagVariable(projectId, segmentId, spec, capturedAt);
        }
        for (EventVariableSpec spec : specs.eventSpecs()) {
            refreshEventVariable(projectId, segmentId, spec, capturedAt);
        }
        refreshVariableStats(projectId, segmentId, specs.variableSources(), capturedAt);
    }

    private void initializeMemberSnapshots(Long segmentId, LocalDateTime capturedAt) {
        jdbcTemplate.update(
                "UPDATE segment_members "
                        + "SET variables_json = JSON_OBJECT(), variables_captured_at = :capturedAt "
                        + "WHERE segment_id = :segmentId",
                Map.of("segmentId", segmentId, "capturedAt", capturedAt));
    }

    private void refreshTagVariable(
            Long projectId,
            Long segmentId,
            TagVariableSpec spec,
            LocalDateTime capturedAt
    ) {
        String sql =
                "UPDATE segment_members "
                        + "JOIN user_tags ut "
                        + "  ON ut.project_id = :projectId "
                        + " AND ut.user_id = segment_members.user_id "
                        + " AND ut.tag_key = :variableKey "
                        + "SET variables_json = JSON_SET("
                        + "        COALESCE(variables_json, JSON_OBJECT()), "
                        + "        :jsonPath, "
                        + "        ut.tag_value"
                        + "    ), "
                        + "    variables_captured_at = :capturedAt "
                        + "WHERE segment_id = :segmentId "
                        + "AND ut.tag_value IS NOT NULL";

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("segmentId", segmentId);
        params.put("variableKey", spec.key());
        params.put("jsonPath", mysqlJsonPath(spec.key()));
        params.put("capturedAt", capturedAt);
        jdbcTemplate.update(sql, params);
    }

    private void refreshEventVariable(
            Long projectId,
            Long segmentId,
            EventVariableSpec spec,
            LocalDateTime capturedAt
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("segmentId", segmentId);
        params.put("categoryId", spec.categoryId());
        params.put("jsonPath", mysqlJsonPath(spec.key()));
        params.put("capturedAt", capturedAt);

        String sql =
                "UPDATE segment_members sm "
                        + "LEFT JOIN ("
                        + " SELECT sm2.user_id, MAX(JSON_UNQUOTE(JSON_EXTRACT(ev.event_data, :jsonPath))) AS variable_value"
                        + " FROM segment_members sm2"
                        + " JOIN events ev FORCE INDEX (idx_events_segment_filter_lookup)"
                        + "   ON ev.project_id = :projectId"
                        + "  AND ev.user_id = sm2.user_id"
                        + "  AND ev.category_id = :categoryId"
                        + periodClause(spec.period(), "ev", params)
                        + eventFilterClause(spec.filters(), "ev", params)
                        + " WHERE sm2.segment_id = :segmentId"
                        + " GROUP BY sm2.user_id"
                        + ") v ON v.user_id = sm.user_id "
                        + "SET sm.variables_json = JSON_SET("
                        + "        COALESCE(sm.variables_json, JSON_OBJECT()), "
                        + "        :jsonPath, "
                        + "        v.variable_value"
                        + "    ), "
                        + "    sm.variables_captured_at = :capturedAt "
                        + "WHERE sm.segment_id = :segmentId "
                        + "AND v.variable_value IS NOT NULL";

        jdbcTemplate.update(sql, params);
    }

    private void refreshVariableStats(
            Long projectId,
            Long segmentId,
            Map<String, String> variableSources,
            LocalDateTime capturedAt
    ) {
        jdbcTemplate.update(
                "DELETE FROM segment_variable_stats "
                        + "WHERE segment_id = :segmentId AND source_type IN ('USER_TAG', 'EVENT_CONTEXT')",
                Map.of("segmentId", segmentId));

        for (Map.Entry<String, String> entry : variableSources.entrySet()) {
            String variableKey = entry.getKey();
            Map<String, Object> params = new HashMap<>();
            params.put("projectId", projectId);
            params.put("segmentId", segmentId);
            params.put("variableKey", variableKey);
            params.put("sourceType", entry.getValue());
            params.put("jsonPath", mysqlJsonPath(variableKey));
            params.put("updatedAt", capturedAt);

            String sql =
                    "INSERT INTO segment_variable_stats ("
                            + "project_id, segment_id, variable_key, source_type, "
                            + "available_count, missing_count, status, sample_value, updated_at"
                            + ") "
                            + "SELECT :projectId, :segmentId, :variableKey, :sourceType, "
                            + "       COALESCE(SUM(JSON_UNQUOTE(JSON_EXTRACT(variables_json, :jsonPath)) IS NOT NULL), 0), "
                            + "       COUNT(*) - COALESCE(SUM(JSON_UNQUOTE(JSON_EXTRACT(variables_json, :jsonPath)) IS NOT NULL), 0), "
                            + "       CASE "
                            + "         WHEN COALESCE(SUM(JSON_UNQUOTE(JSON_EXTRACT(variables_json, :jsonPath)) IS NOT NULL), 0) = 0 THEN 'MISSING' "
                            + "         WHEN COALESCE(SUM(JSON_UNQUOTE(JSON_EXTRACT(variables_json, :jsonPath)) IS NOT NULL), 0) = COUNT(*) THEN 'READY' "
                            + "         ELSE 'PARTIAL' "
                            + "       END, "
                            + "       MAX(JSON_UNQUOTE(JSON_EXTRACT(variables_json, :jsonPath))), "
                            + "       :updatedAt "
                            + "FROM segment_members "
                            + "WHERE segment_id = :segmentId";

            jdbcTemplate.update(sql, params);
        }
    }

    private static VariableSpecs collectVariableSpecs(GroupNode root) {
        List<TagVariableSpec> tagSpecs = new ArrayList<>();
        List<EventVariableSpec> eventSpecs = new ArrayList<>();
        collect(root, tagSpecs, eventSpecs);
        return new VariableSpecs(tagSpecs, eventSpecs);
    }

    private static void collect(
            ConditionNode node,
            List<TagVariableSpec> tagSpecs,
            List<EventVariableSpec> eventSpecs
    ) {
        switch (node) {
            case GroupNode group -> group.conditions().forEach(child -> collect(child, tagSpecs, eventSpecs));
            case com.b101.butterfly.segment.domain.condition.TagNode tag -> {
                if (isTemplateVariableKey(tag.key())) {
                    tagSpecs.add(new TagVariableSpec(tag.key()));
                }
            }
            case EventNode event -> {
                if (event.filters() != null) {
                    for (EventFilter filter : event.filters()) {
                        if (isTemplateVariableKey(filter.key())) {
                            eventSpecs.add(new EventVariableSpec(
                                    filter.key(),
                                    event.categoryId(),
                                    event.period(),
                                    event.filters()));
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    private static String periodClause(Period period, String alias, Map<String, Object> params) {
        LocalDate today = LocalDate.now(SEOUL);
        return switch (period) {
            case Period.Relative r -> {
                String paramName = "eventFromDate";
                params.put(paramName, today.minusDays(r.days()));
                yield " AND " + alias + ".occurred_at >= :" + paramName;
            }
            case Period.Absolute a -> {
                String startParam = "eventStartDate";
                params.put(startParam, a.startDate());
                String endParam = "eventEndExclusiveDate";
                params.put(endParam, a.endDate().plusDays(1));
                yield " AND " + alias + ".occurred_at >= :" + startParam
                        + " AND " + alias + ".occurred_at < :" + endParam;
            }
            case Period.Cumulative ignored -> "";
        };
    }

    private static String eventFilterClause(
            List<EventFilter> filters,
            String alias,
            Map<String, Object> params
    ) {
        StringBuilder clause = new StringBuilder();
        for (int i = 0; i < filters.size(); i++) {
            EventFilter filter = filters.get(i);
            String pathParam = "eventFilterPath" + i;
            String valParam = "eventFilterVal" + i;
            params.put(pathParam, mysqlJsonPath(filter.key()));
            params.put(valParam, eventFilterBindValue(filter.op(), filter.value()));
            String extracted =
                    "JSON_UNQUOTE(JSON_EXTRACT(" + alias + ".event_data, :" + pathParam + "))";
            String columnExpr =
                    switch (filter.op()) {
                        case GT, LT, GTE, LTE -> "CAST(" + extracted + " AS DECIMAL(30, 10))";
                        default -> extracted;
                    };
            clause.append(" AND ").append(opClause(columnExpr, filter.op(), valParam));
        }
        return clause.toString();
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

    private static boolean isTemplateVariableKey(String key) {
        return key != null && TEMPLATE_VARIABLE_KEY.matcher(key).matches();
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
        if (value.isTextual()) return value.asText();
        if (value.isNumber()) return value.numberValue();
        if (value.isBoolean()) return value.booleanValue();
        return value.asText();
    }

    private static String scalarAsText(JsonNode value) {
        return value.asText();
    }

    private static String mysqlJsonPath(String key) {
        String escaped = key.replace("\\", "\\\\").replace("\"", "\\\"");
        return "$.\"" + escaped + "\"";
    }

    private record TagVariableSpec(String key) {
    }

    private record EventVariableSpec(
            String key,
            Long categoryId,
            Period period,
            List<EventFilter> filters
    ) {
    }

    private record VariableSpecs(
            List<TagVariableSpec> tagSpecs,
            List<EventVariableSpec> eventSpecs
    ) {
        boolean isEmpty() {
            return tagSpecs.isEmpty() && eventSpecs.isEmpty();
        }

        Map<String, String> variableSources() {
            Map<String, String> sources = new LinkedHashMap<>();
            for (TagVariableSpec spec : tagSpecs) {
                sources.putIfAbsent(spec.key(), "USER_TAG");
            }
            for (EventVariableSpec spec : eventSpecs) {
                sources.put(spec.key(), "EVENT_CONTEXT");
            }
            return sources;
        }
    }
}
