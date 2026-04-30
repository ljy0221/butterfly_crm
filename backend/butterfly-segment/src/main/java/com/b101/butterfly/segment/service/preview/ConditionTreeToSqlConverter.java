package com.b101.butterfly.segment.service.preview;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.DeviceNode;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.UserNode;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * TAG/USER/DEVICE 조건 트리를 named parameter SQL로 변환한다.
 *
 * <p>EVENT 노드를 만나면 {@link EventConditionNotReadyException}을 던져 event 모듈 합류(Phase 6)
 * 전까지의 미지원을 PreviewService에 알린다.
 *
 * <p>USER/DEVICE의 {@code key}는 SQL identifier로 들어가므로 화이트리스트로 강제한다 — 임의 컬럼 노출 차단.
 * TAG의 {@code key}는 행 데이터(`user_tags.key` 컬럼 값)이므로 placeholder로 안전하게 바인딩.
 */
@Component
public class ConditionTreeToSqlConverter {

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
        AtomicInteger counter = new AtomicInteger();
        SqlFragment body = walk(root, counter);

        Map<String, Object> params = new HashMap<>(body.params());
        params.put("projectId", projectId);

        String sql =
                "SELECT u.user_id FROM users u WHERE u.project_id = :projectId AND ("
                        + body.sql()
                        + ")";
        return new SqlFragment(sql, params);
    }

    private SqlFragment walk(ConditionNode node, AtomicInteger counter) {
        return switch (node) {
            case GroupNode g -> walkGroup(g, counter);
            case TagNode t -> tagFragment(t, counter);
            case UserNode u -> userFragment(u, counter);
            case DeviceNode d -> deviceFragment(d, counter);
            case EventNode ignored -> throw new EventConditionNotReadyException();
        };
    }

    private SqlFragment walkGroup(GroupNode group, AtomicInteger counter) {
        List<SqlFragment> fragments =
                group.conditions().stream().map(child -> walk(child, counter)).toList();
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

        String opClause = opClause("ut.value", tag.op(), valParam);
        String sql =
                "EXISTS (SELECT 1 FROM user_tags ut WHERE ut.user_id = u.user_id"
                        + " AND ut.`key` = :" + keyParam
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
}
