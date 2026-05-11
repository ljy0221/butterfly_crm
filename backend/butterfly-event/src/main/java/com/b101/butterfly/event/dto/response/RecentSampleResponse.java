package com.b101.butterfly.event.dto.response;

import com.b101.butterfly.event.entity.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 카테고리별 최근 1건 이벤트 샘플. {@code event_data} JSON 을 Map 으로 풀어 frontend 에 노출.
 *
 * <p>최근 1건이 없으면 null 반환 (frontend 도 nullable 로 처리).
 */
public record RecentSampleResponse(
        String occurredAt,
        Map<String, Object> eventData) {

    public static RecentSampleResponse of(Event event, ObjectMapper objectMapper) {
        Map<String, Object> data = parse(event.getEventData(), objectMapper);
        return new RecentSampleResponse(
                event.getOccurredAt() == null ? null : event.getOccurredAt().toString(),
                data);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
