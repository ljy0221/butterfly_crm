package com.b101.butterfly.segment.messaging;

import java.time.LocalDateTime;

/**
 * event 모듈이 "이벤트 저장 + agg 반영 완료" 토픽({@value #TOPIC})에 publish하는 페이로드의
 * segment 측 미러. event 모듈의 {@code EventPersistedPayload}와 필드 100% 일치 (이씨 [-21]
 * 머지로 확정, 2026-05-06 기준).
 *
 * <p>모듈 격리를 위해 segment가 자체 record를 보유 — event 모듈을 직접 import하지 않는다.
 * 역직렬화는 {@link com.b101.butterfly.segment.config.SegmentKafkaConsumerConfig}의
 * {@code eventOccurredConsumerFactory}에서 본 record로 강제. JSON 필드명만 일치하면 동작한다.
 *
 * <p>필드 (segment 매칭에 필요한 5개):
 * <ul>
 *   <li>{@code eventUuid} — dedup 키 (event:dedup:{eventUuid} TTL 10m)</li>
 *   <li>{@code projectId} — 활성 세그먼트 캐시 키</li>
 *   <li>{@code userId} — 매칭 평가 대상</li>
 *   <li>{@code categoryId} — 활성 세그먼트 캐시 키 (ADR-0015 정합)</li>
 *   <li>{@code occurredAt} — Period 평가</li>
 * </ul>
 *
 * <p>event 측 publish 보장 (이씨 [-21] / ADR-0014 events-persisted-topic):
 * EventConsumer가 INSERT events + UPSERT event_daily_agg 트랜잭션 commit 후 AFTER_COMMIT
 * 단계에서 publish — segment 매칭 평가 시점에 agg가 반영돼 있음을 보장.
 */
public record EventOccurredMessage(
        String eventUuid,
        Long projectId,
        Long userId,
        Long categoryId,
        LocalDateTime occurredAt
) {

    /** event 측 {@code EventPersistedProducer.TOPIC}와 동일 — 협의 결과 확정. */
    public static final String TOPIC = "events.persisted";
}
