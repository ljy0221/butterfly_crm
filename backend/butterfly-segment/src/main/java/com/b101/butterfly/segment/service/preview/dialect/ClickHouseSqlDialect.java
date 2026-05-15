package com.b101.butterfly.segment.service.preview.dialect;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * ClickHouse dialect (S14P31B101-275).
 *
 * <p>{@code butterfly.clickhouse.enabled=true} 일 때 활성. JSON attribute 추출에 ClickHouse 의
 * {@code JSONExtractString} / {@code JSONExtractFloat} 함수를 사용. path 는 named param 으로 바인딩
 * 하지 않고 literal 로 embed 한다 — ClickHouse 의 path 인자는 vararg-like 라 named param 으로
 * 표현하기 까다롭고, key 는 customer-controlled 입력이므로 alphanum + '_' + '-' 외 문자는 거부해
 * SQL injection 을 차단한다.
 */
@Component("clickHouseSqlDialect")
@ConditionalOnProperty(name = "butterfly.clickhouse.enabled", havingValue = "true")
public class ClickHouseSqlDialect implements SqlDialect {

    /** ClickHouse 의 path literal 로 embed 할 수 있는 안전한 key 패턴 — SQL injection 차단. */
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_.\\-]+");

    @Override
    public PredicateFragment filterPredicate(
            String column, String key, Operator op, String valParamName) {
        requireSafeKey(key);

        String extractFn =
                switch (op) {
                    case GT, LT, GTE, LTE -> "JSONExtractFloat";
                    default -> "JSONExtractString";
                };
        String extracted = extractFn + "(" + column + ", '" + key + "')";
        String predicate = opClause(extracted, op, valParamName);
        return new PredicateFragment(predicate, Map.of());
    }

    @Override
    public String name() {
        return "clickhouse";
    }

    private static void requireSafeKey(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new BusinessException(
                    SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE,
                    "ClickHouse dialect 에서 attribute key 는 [A-Za-z0-9_.-] 만 허용: " + key);
        }
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
            case CONTAINS -> "position(" + columnExpr + ", :" + paramName + ") > 0";
        };
    }
}
