package com.b101.butterfly.segment.service.preview.dialect;

import com.b101.butterfly.segment.domain.condition.Operator;

import java.util.Map;

/**
 * Segment SQL 생성의 dialect 추상화 (S14P31B101-275).
 *
 * <p>{@code ConditionTreeToSqlConverter} 의 SQL 모양 중 dialect-specific 한 부분 (JSON attribute 추출)
 * 을 본 인터페이스에 위임한다. MySQL ({@link MysqlSqlDialect}) 와 ClickHouse
 * ({@link ClickHouseSqlDialect}) 두 구현. 활성 빈은 {@code butterfly.clickhouse.enabled} 에 따라 결정.
 */
public interface SqlDialect {

    /**
     * EVENT filter 의 JSON attribute 비교 predicate SQL fragment + 바인딩 파라미터.
     *
     * <p>예시:
     * <ul>
     *   <li>MySQL: {@code JSON_UNQUOTE(JSON_EXTRACT(ev.event_data, '$."plan"')) = :eventFilterVal0_0}</li>
     *   <li>ClickHouse: {@code JSONExtractString(ev.event_data, 'plan') = :eventFilterVal0_0}</li>
     * </ul>
     *
     * <p>JSON path 가 named param 으로 바인딩되는지 (MySQL) literal 로 embed 되는지 (ClickHouse) 는
     * dialect 가 결정. ClickHouse path 는 alphanum 검증 후 escape 처리.
     *
     * @param column 추출 대상 column 식 (예: {@code "ev.event_data"})
     * @param key attribute key (예: {@code "plan"})
     * @param op 비교 연산자 (숫자 비교면 dialect 가 숫자 추출 함수로 분기)
     * @param valParamName 비교 값 named param 이름
     * @return predicate fragment + dialect 가 추가한 path 등 named param map
     */
    PredicateFragment filterPredicate(String column, String key, Operator op, String valParamName);

    String name();

    /**
     * predicate SQL + 본 dialect 에서 추가로 바인딩이 필요한 named param 묶음.
     * value 자체 (valParamName) 는 caller 가 별도로 바인딩.
     */
    record PredicateFragment(String sql, Map<String, Object> extraParams) {}
}
