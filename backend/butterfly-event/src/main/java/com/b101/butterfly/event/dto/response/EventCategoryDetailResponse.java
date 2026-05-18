package com.b101.butterfly.event.dto.response;

import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.EventCategoryType;

import java.time.LocalDateTime;

/**
 * 카테고리 단건 상세 응답.
 *
 * <p>frontend {@code EventCategoryRecord} 와 1:1. {@code displayName} 과 {@code description} 은
 * backend 의 단일 {@code description} 컬럼을 공유한다. 시각 컬럼은 ISO-8601 LocalDateTime 문자열 (offset 없음).
 */
public record EventCategoryDetailResponse(
        Long categoryId,
        Long projectId,
        String categoryName,
        String displayName,
        String description,
        String kind,
        long eventCount,
        double successRate,
        String lastCollectedAt,
        String createdAt,
        String updatedAt) {

    public static EventCategoryDetailResponse of(EventCategory category) {
        return of(category, 0L, null);
    }

    public static EventCategoryDetailResponse of(EventCategory category, long eventCount,
                                                 LocalDateTime lastCollectedAt) {
        String description = category.getDescription();
        return new EventCategoryDetailResponse(
                category.getId(),
                category.getProjectId(),
                category.getName(),
                description,
                description,
                toKind(category.getType()),
                eventCount,
                100.0,
                toIsoString(lastCollectedAt),
                toIsoString(category.getCreatedAt()),
                toIsoString(category.getUpdatedAt()));
    }

    private static String toKind(EventCategoryType type) {
        return type == EventCategoryType.DEFAULT ? "BUILTIN" : "CUSTOM";
    }

    private static String toIsoString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toString();
    }
}
