package com.b101.butterfly.segment.service.preview.dialect;

import com.b101.butterfly.segment.domain.condition.Operator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MySQL dialect (S14P31B101-275).
 *
 * <p>{@code butterfly.clickhouse.enabled=false} (디폴트) 일 때 활성. true 일 때는 비활성되고
 * {@link ClickHouseSqlDialect} 가 대신 등록된다.
 */
@Component
@ConditionalOnProperty(name = "butterfly.clickhouse.enabled", havingValue = "false", matchIfMissing = true)
public class MysqlSqlDialect implements SqlDialect {

    @Override
    public PredicateFragment filterPredicate(
            String column, String key, Operator op, String valParamName) {
        String pathParam = valParamName + "_path";
        String extracted = "JSON_UNQUOTE(JSON_EXTRACT(" + column + ", :" + pathParam + "))";
        String columnExpr =
                switch (op) {
                    case GT, LT, GTE, LTE -> "CAST(" + extracted + " AS DECIMAL(30, 10))";
                    default -> extracted;
                };

        Map<String, Object> extra = new HashMap<>();
        extra.put(pathParam, mysqlJsonPath(key));

        return new PredicateFragment(opClause(columnExpr, op, valParamName), extra);
    }

    @Override
    public String name() {
        return "mysql";
    }

    private static String mysqlJsonPath(String key) {
        String escaped = key.replace("\\", "\\\\").replace("\"", "\\\"");
        return "$.\"" + escaped + "\"";
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
}
