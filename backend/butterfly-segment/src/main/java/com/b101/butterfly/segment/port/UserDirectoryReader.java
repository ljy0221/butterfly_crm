package com.b101.butterfly.segment.port;

import java.util.List;
import java.util.Map;

/**
 * 미리보기 SQL을 실행해 매칭된 user_id를 조회한다. event 모듈이 진짜 구현을 등록한다.
 *
 * <p>SQL은 {@code ConditionTreeToSqlConverter}가 생성하고, 본 reader는 placeholder 바인딩 후
 * PreparedStatement로 안전하게 실행한다.
 */
public interface UserDirectoryReader {

    List<Long> queryUserIds(String sql, Map<String, Object> params);

    /**
     * 매칭 user_id 의 개수만 카운트 (S14P31B101-275). 미리보기에서 list 전체를 JVM 로 받지 않고
     * COUNT 쿼리로 결과 수만 받는다.
     *
     * <p>MySQL 어댑터: {@code SELECT COUNT(*) FROM (<selectSql>) AS m}
     * ClickHouse 어댑터: {@code SELECT count() FROM (<selectSql>) AS m}
     *
     * @param selectSql {@code SELECT user_id ...} 형태의 SQL (named parameters)
     * @param params 바인딩 파라미터
     * @return 매칭 user 수
     */
    long countUserMatches(String selectSql, Map<String, Object> params);

    /**
     * filtered/mixed 트리 빌드 최적화 — 매칭 user_id 를 segment_members 에 단일
     * {@code INSERT ... SELECT} statement 로 직접 적재한다 (S14P31B101-269).
     *
     * <p>기존 흐름은 {@link #queryUserIds} 로 List 를 메모리에 받아 chunk batchUpdate INSERT —
     * Java 왕복 + 메모리 비용 + chunk 별 트랜잭션 오버헤드가 발생. 본 메서드는 SELECT 결과를
     * 한 statement 로 segment_members 에 적재해 그 비용을 제거한다 ({@code INSERT IGNORE} 안전망).
     *
     * <p>호출 전제: 동일 segment 의 기존 멤버는 이미 비워진 상태 (PK 충돌 0). 호출자가
     * {@code deleteBySegmentId} 후 호출한다.
     *
     * <p>stub 구현 (event 모듈 미합류 시) 은 0 을 반환 — 미리보기/빌드 결과 0 건.
     *
     * @param segmentId 적재 대상 segment ID
     * @param selectSql {@code SELECT user_id ...} 형태의 SQL (named parameters)
     * @param params 바인딩 파라미터
     * @return 적재된 row 수
     */
    long materializeUserMatchesIntoSegment(
            Long segmentId, String selectSql, Map<String, Object> params);
}
