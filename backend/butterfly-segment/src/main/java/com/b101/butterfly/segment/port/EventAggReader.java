package com.b101.butterfly.segment.port;

import com.b101.butterfly.segment.domain.condition.EventNode;

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
}
