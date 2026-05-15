package com.b101.butterfly.event.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Kafka 토픽 {@code events.collected} 의 메시지 payload (S14P31B101-21).
 *
 * <p>각 필드에 {@link JsonProperty}(snake_case) 명시 — Spring {@code JsonSerializer} 의 default 가
 * Java 컨벤션 (camelCase) 그대로 직렬화하지만, consumer (ClickHouse Kafka Engine / worker
 * {@code EventConsumer}) 가 DB 컬럼 컨벤션 (snake_case) 으로 받는 게 표준. JSON key 를 snake_case
 * 로 박아 publisher / storage schema 의 컨벤션 일관성 유지.
 *
 * <p>S14P31B101-300 2차 commit (camelCase queue + MV aliasing) 의 단기 우회로를 정석으로
 * 회복하는 follow-up. 본 변경 직후 ClickHouse 측 schema 도 snake_case 로 복귀 필요 (PR B).
 */
public record EventPayload(
        @JsonProperty("event_uuid")     String eventUuid,
        @JsonProperty("project_id")     Long projectId,
        @JsonProperty("user_id")        Long userId,
        @JsonProperty("device_id")      Long deviceId,
        @JsonProperty("category_id")    Long categoryId,
        @JsonProperty("occurred_at")    LocalDateTime occurredAt,
        @JsonProperty("schema_version") Integer schemaVersion,
        @JsonProperty("event_data")     String eventData
) {
}
