package com.b101.butterfly.segment.messaging;

import java.time.Instant;

/**
 * Segment rebuild 요청 수락 도메인 이벤트 (S14P31B101-269).
 *
 * <p>{@link com.b101.butterfly.segment.service.SegmentRebuildService} 의 트랜잭션 안에서 발행되고,
 * {@link SegmentRebuildProducer} 가 {@code @TransactionalEventListener(AFTER_COMMIT)} 으로 받아
 * Kafka 메시지로 변환·발행한다.
 *
 * <p><b>왜 AFTER_COMMIT 인가</b>: 이전엔 publish 가 트랜잭션 안에서 직접 일어났다. 그 사이 worker 가
 * 메시지를 consume → {@code markBuildingIfPossible} 로 segments 행을 BUILDING + token 마킹 → commit.
 * 그 후 {@code SegmentRebuildService} 트랜잭션 commit 시 JPA dirty flush 가 worker 의 변경을
 * 덮어쓰는 lost update 가 발생했다. AFTER_COMMIT 으로 발행 시점을 늦추면 worker 가 메시지를 받기
 * 시작했을 때 DB 가 이미 markPending 적용된 일관 상태라 race 가 사라진다.
 *
 * <p>트랜잭션 rollback 시에는 listener 가 호출되지 않으므로 Kafka 메시지도 발행되지 않아 DB 와
 * 메시지 큐 일관성이 보장된다.
 */
public record SegmentRebuildAcceptedEvent(
        Long segmentId,
        Long projectId,
        Long memberId,
        Instant requestedAt,
        String conditionTreeHash) {
}
