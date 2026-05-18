package com.b101.butterfly.event.dto.response;

import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.EventCategoryType;

import java.time.LocalDateTime;

/**
 * 카테고리 목록 응답 element.
 *
 * <p>frontend {@code EventCategorySummary} 와 1:1 매핑. {@code kind} 는 backend {@link
 * EventCategoryType#DEFAULT} 를 frontend 가 기대하는 {@code "BUILTIN"} 으로 변환한다.
 *
 * <p>{@code displayName} 과 {@code description} 은 backend 의 단일 {@code description} 컬럼을 공유한다
 * (frontend 가 두 개 필드를 갖지만 운영상 같은 텍스트로 충분 — 별도 컬럼 분리 없이 단순화).
 */
public record EventCategorySummaryResponse(
        Long categoryId,
        String categoryName,
        String displayName,
        String description,
        String kind,
        int propertyCount,
        long eventCount,
        double successRate,
        String lastCollectedAt) {

    public static EventCategorySummaryResponse of(EventCategory category, int propertyCount) {
        return of(category, propertyCount, 0L, null);
    }

    public static EventCategorySummaryResponse of(EventCategory category, int propertyCount,
                                                  long eventCount, LocalDateTime lastCollectedAt) {
        String description = category.getDescription();
        return new EventCategorySummaryResponse(
                category.getId(),
                category.getName(),
                description,
                description,
                toKind(category.getType()),
                propertyCount,
                eventCount,
                100.0,
                toIsoString(lastCollectedAt));
    }

    private static String toKind(EventCategoryType type) {
        return type == EventCategoryType.DEFAULT ? "BUILTIN" : "CUSTOM";
    }

    private static String toIsoString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toString();
    }
}
