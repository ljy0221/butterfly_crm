package com.b101.butterfly.segment.messaging;

import java.time.LocalDateTime;

/**
 * event 모듈이 "이벤트 저장 + agg 반영 완료" 토픽에 publish하는 페이로드 placeholder.
 *
 * <p><b>주의</b>: 토픽명·페이로드 형식 모두 event 팀과 협의 진행 중이다
 * ({@code backend/docs/private/request-event-persisted-topic.md} 참고). 답변 받는 대로
 * {@link #TOPIC} 상수와 본 record 필드를 그쪽 사양에 맞춰 갱신.
 *
 * <p>현재 가정:
 * <ul>
 *   <li>토픽명 placeholder: {@code events.persisted}</li>
 *   <li>필드: segment 매칭에 필요한 5개만 (eventUuid / projectId / userId / categoryId / occurredAt)</li>
 *   <li>{@code deviceId}, {@code eventData}, {@code schemaVersion}은 segment 미사용 — event 측이 보내든 말든 무관, 무시</li>
 * </ul>
 *
 * <p>역직렬화는 {@link com.b101.butterfly.segment.config.SegmentKafkaConsumerConfig}의
 * {@code eventOccurredConsumerFactory}에서 강제. JSON 필드명만 일치하면 동작.
 */
public record EventOccurredMessage(
        String eventUuid,
        Long projectId,
        Long userId,
        Long categoryId,
        LocalDateTime occurredAt
) {

    /** placeholder 토픽명. event 협의 후 확정 시 한 줄 수정. */
    public static final String TOPIC = "events.persisted";
}
