package com.b101.butterfly.segment.port;

import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.service.preview.EventConditionNotReadyException;

import java.util.stream.Stream;

/**
 * EVENT 조건 평가용 포트. event 모듈의 event_daily_agg를 읽어 매칭 user_id를 조회.
 *
 * <p>구현체는 event 모듈의 EventAggAdapter. 미합류 시 StubEventAggReader가 등록되며
 * 호출 시 EventConditionNotReadyException을 던진다.
 *
 * <p>Stream 반환 — build job에서 매칭 user 수가 100M+ 가능성 있을 때 메모리 회피용.
 * 호출자는 try-with-resources 또는 명시적 close 권장.
 */
public interface EventAggReader {

    Stream<Long> queryMatchingUserIds(EventNode node, EventQueryContext context);

    /**
     * 단일 사용자의 EVENT 조건 매칭 여부 검사. Phase 6-3 실시간 매칭 컨슈머에서 사용.
     *
     * <p>기본 구현은 {@link #queryMatchingUserIds}를 호출해 결과 스트림에서 userId 일치 여부를
     * 검사한다 — 안전하지만 비효율적. event 측 어댑터에서 단일 사용자 SUM HAVING 쿼리로
     * override 권장 (예: {@code WHERE user_id=:uid AND ... HAVING SUM(event_count) >= :minCount}).
     *
     * @param node 평가할 EVENT 조건
     * @param userId 검사 대상 사용자 ID
     * @param context 평가 컨텍스트 (projectId / asOfDate / timeZone)
     * @return true = 매칭, false = 미매칭
     */
    default boolean userMatches(EventNode node, Long userId, EventQueryContext context) {
        try (Stream<Long> stream = queryMatchingUserIds(node, context)) {
            return stream.anyMatch(uid -> uid.equals(userId));
        }
    }

    /**
     * filter 없는 EVENT 단독 트리 빌드 최적화 — 매칭 user_id를 segment_members에 단일
     * {@code INSERT ... SELECT} statement로 직접 적재한다. Stream + JPA saveAll 경로 대비
     * 1M row 기준 약 80배 빠르다 (벤치 ~67분 → ~48초).
     *
     * <p>구현체(event 측 EventAggAdapter)는 같은 DB의 {@code event_daily_agg}를 GROUP BY user_id
     * HAVING SUM(event_count) &gt;= minCount로 집계해 segment_members에 INSERT한다.
     * cross-table 최적화이며, 호출자(SegmentBuildJob)는 사전에 동일 segment의 기존 member를
     * 모두 삭제해 PK 충돌이 없는 상태에서 호출한다.
     *
     * <p>기본 구현은 {@link EventConditionNotReadyException}을 던진다 — event 모듈 미합류 시
     * 동일한 정책.
     *
     * @param segmentId 적재 대상 segment ID
     * @param node 평가할 EVENT 조건 (filter 없음 전제)
     * @param context 평가 컨텍스트 (projectId / asOfDate / timeZone)
     * @return INSERT된 row 수
     */
    default long materializeEventMatchesIntoSegment(
            Long segmentId, EventNode node, EventQueryContext context) {
        throw new EventConditionNotReadyException();
    }
}
