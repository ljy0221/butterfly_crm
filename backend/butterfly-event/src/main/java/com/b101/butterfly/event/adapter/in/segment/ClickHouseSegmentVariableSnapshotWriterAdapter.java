package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.event.config.ClickHouseConfig.ClickHouseAccess;
import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.domain.condition.TagNode;
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
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "butterfly.clickhouse.enabled", havingValue = "true")
public class ClickHouseSegmentVariableSnapshotWriterAdapter implements SegmentVariableSnapshotWriter {

    private static final int CHUNK_SIZE = 1000;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Pattern TEMPLATE_VARIABLE_KEY =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern CLICKHOUSE_SAFE_KEY =
            Pattern.compile("[A-Za-z0-9_.\\-]+");

    private final NamedParameterJdbcTemplate clickHouseJdbcTemplate;
    private final NamedParameterJdbcTemplate mysqlJdbcTemplate;

    public ClickHouseSegmentVariableSnapshotWriterAdapter(
            ClickHouseAccess clickHouseAccess,
            NamedParameterJdbcTemplate mysqlJdbcTemplate) {
        this.clickHouseJdbcTemplate = clickHouseAccess.jdbcTemplate();
        this.mysqlJdbcTemplate = mysqlJdbcTemplate;
    }

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
            refreshEventVariableFromClickHouse(projectId, segmentId, spec, capturedAt);
        }
        refreshVariableStats(projectId, segmentId, specs.variableSources(), capturedAt);
    }

    private void initializeMemberSnapshots(Long segmentId, LocalDateTime capturedAt) {
        mysqlJdbcTemplate.update(
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
        mysqlJdbcTemplate.update(sql, params);
    }

    private void refreshEventVariableFromClickHouse(
            Long projectId,
            Long segmentId,
            EventVariableSpec spec,
            LocalDateTime capturedAt
    ) {
        requireClickHouseSafeKey(spec.key());

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("categoryId", spec.categoryId());

        String sql =
                "SELECT user_id, anyLast(JSONExtractString(event_data, '" + spec.key() + "')) AS variable_value "
                        + "FROM events "
                        + "WHERE project_id = :projectId "
                        + "AND category_id = :categoryId"
                        + clickHousePeriodClause(spec.period(), params)
                        + clickHouseFilterClause(spec.filters(), params)
                        + " GROUP BY user_id";

        long start = System.nanoTime();
        String snapshotRunId = UUID.randomUUID().toString();
        try {
            try (Stream<VariableRow> rows =
                         clickHouseJdbcTemplate.queryForStream(
                                 sql,
                                 params,
                                 (rs, rowNum) -> new VariableRow(
                                         rs.getLong("user_id"),
                                         rs.getString("variable_value")))) {
                List<SqlParameterSource> buffer = new ArrayList<>(CHUNK_SIZE);
                rows.forEach(row -> {
                    if (row.value() == null) {
                        return;
                    }
                    buffer.add(new MapSqlParameterSource()
                            .addValue("snapshotRunId", snapshotRunId)
                            .addValue("segmentId", segmentId)
                            .addValue("variableKey", spec.key())
                            .addValue("userId", row.userId())
                            .addValue("variableValue", row.value()));
                    if (buffer.size() >= CHUNK_SIZE) {
                        flushStageBatch(buffer);
                        buffer.clear();
                    }
                });
                if (!buffer.isEmpty()) {
                    flushStageBatch(buffer);
                }
            }
            updateEventVariableFromStage(snapshotRunId, segmentId, spec.key(), capturedAt);
        } finally {
            clearStage(snapshotRunId);
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        log.info(
                "ClickHouse segment variable snapshot 완료: segmentId={}, key={}, elapsedMs={}",
                segmentId,
                spec.key(),
                millis);
    }

    private void flushStageBatch(List<SqlParameterSource> buffer) {
        mysqlJdbcTemplate.batchUpdate(
                "INSERT INTO segment_variable_snapshot_stage ("
                        + "snapshot_run_id, segment_id, variable_key, user_id, variable_value"
                        + ") VALUES ("
                        + ":snapshotRunId, :segmentId, :variableKey, :userId, :variableValue"
                        + ") "
                        + "ON DUPLICATE KEY UPDATE "
                        + "variable_value = VALUES(variable_value), "
                        + "created_at = CURRENT_TIMESTAMP(3)",
                buffer.toArray(SqlParameterSource[]::new));
    }

    private void updateEventVariableFromStage(
            String snapshotRunId,
            Long segmentId,
            String variableKey,
            LocalDateTime capturedAt
    ) {
        mysqlJdbcTemplate.update(
                "UPDATE segment_members sm "
                        + "JOIN segment_variable_snapshot_stage st "
                        + "  ON st.snapshot_run_id = :snapshotRunId "
                        + " AND st.user_id = sm.user_id "
                        + "SET sm.variables_json = JSON_SET("
                        + "        COALESCE(sm.variables_json, JSON_OBJECT()), "
                        + "        :jsonPath, "
                        + "        st.variable_value"
                        + "    ), "
                        + "    sm.variables_captured_at = :capturedAt "
                        + "WHERE sm.segment_id = :segmentId "
                        + "AND st.variable_value IS NOT NULL",
                Map.of(
                        "snapshotRunId", snapshotRunId,
                        "segmentId", segmentId,
                        "jsonPath", mysqlJsonPath(variableKey),
                        "capturedAt", capturedAt));
    }

    private void clearStage(String snapshotRunId) {
        mysqlJdbcTemplate.update(
                "DELETE FROM segment_variable_snapshot_stage "
                        + "WHERE snapshot_run_id = :snapshotRunId",
                Map.of("snapshotRunId", snapshotRunId));
    }

    private void refreshVariableStats(
            Long projectId,
            Long segmentId,
            Map<String, String> variableSources,
            LocalDateTime capturedAt
    ) {
        mysqlJdbcTemplate.update(
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

            mysqlJdbcTemplate.update(sql, params);
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
            case TagNode tag -> {
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

    private static String clickHousePeriodClause(Period period, Map<String, Object> params) {
        LocalDate today = LocalDate.now(SEOUL);
        return switch (period) {
            case Period.Relative r -> {
                params.put("eventFromDate", today.minusDays(r.days()));
                yield " AND occurred_at >= :eventFromDate";
            }
            case Period.Absolute a -> {
                params.put("eventStartDate", a.startDate());
                params.put("eventEndExclusiveDate", a.endDate().plusDays(1));
                yield " AND occurred_at >= :eventStartDate AND occurred_at < :eventEndExclusiveDate";
            }
            case Period.Cumulative ignored -> "";
        };
    }

    private static String clickHouseFilterClause(List<EventFilter> filters, Map<String, Object> params) {
        StringBuilder clause = new StringBuilder();
        for (int i = 0; i < filters.size(); i++) {
            EventFilter filter = filters.get(i);
            requireClickHouseSafeKey(filter.key());
            String valParam = "eventFilterVal" + i;
            params.put(valParam, eventFilterBindValue(filter.op(), filter.value()));
            String extractFn =
                    switch (filter.op()) {
                        case GT, LT, GTE, LTE -> "JSONExtractFloat";
                        default -> "JSONExtractString";
                    };
            String extracted = extractFn + "(event_data, '" + filter.key() + "')";
            clause.append(" AND ").append(clickHouseOpClause(extracted, filter.op(), valParam));
        }
        return clause.toString();
    }

    private static String clickHouseOpClause(String columnExpr, Operator op, String paramName) {
        return switch (op) {
            case EQ -> columnExpr + " = :" + paramName;
            case NEQ -> columnExpr + " <> :" + paramName;
            case GT -> columnExpr + " > :" + paramName;
            case LT -> columnExpr + " < :" + paramName;
            case GTE -> columnExpr + " >= :" + paramName;
            case LTE -> columnExpr + " <= :" + paramName;
            case IN -> columnExpr + " IN (:" + paramName + ")";
            case CONTAINS -> "position(" + columnExpr + ", :" + paramName + ") > 0";
        };
    }

    private static Object eventFilterBindValue(Operator op, JsonNode value) {
        if (op == Operator.IN) {
            List<Object> list = new ArrayList<>();
            value.forEach(v -> list.add(scalarAsText(v)));
            return list;
        }
        if (op == Operator.CONTAINS) {
            return value.asText();
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

    private static boolean isTemplateVariableKey(String key) {
        return key != null && TEMPLATE_VARIABLE_KEY.matcher(key).matches();
    }

    private static void requireClickHouseSafeKey(String key) {
        if (key == null || !CLICKHOUSE_SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("ClickHouse event_data key 형식이 올바르지 않습니다: " + key);
        }
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

    private record VariableRow(Long userId, String value) {
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
