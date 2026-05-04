package com.b101.butterfly.event.messaging;

import java.time.LocalDateTime;

/**
 * events.persisted 토픽 페이로드 (ADR-0014).
 * EventConsumer가 DB INSERT + agg UPSERT 트랜잭션을 commit한 후 publish.
 * segment 실시간 매칭 컨슈머가 minCount=1 같은 케이스를 정확히 평가하기 위해 필요한 5 필드만 노출.
 */
public record EventPersistedPayload(
        String eventUuid,
        Long projectId,
        Long userId,
        Long categoryId,
        LocalDateTime occurredAt
) {
}
