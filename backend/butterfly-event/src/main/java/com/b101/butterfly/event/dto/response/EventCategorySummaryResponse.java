package com.b101.butterfly.event.dto.response;

import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.EventCategoryType;

/**
 * 카테고리 목록 응답 element.
 *
 * <p>frontend {@code EventCategorySummary} 와 1:1 매핑. {@code kind} 는 backend {@link
 * EventCategoryType#DEFAULT} 를 frontend 가 기대하는 {@code "BUILTIN"} 으로 변환한다.
 *
 * <p>{@code displayName} 과 {@code description} 은 backend 의 단일 {@code description} 컬럼을 공유한다
 * (frontend 가 두 개 필드를 갖지만 운영상 같은 텍스트로 충분 — 별도 컬럼 분리 없이 단순화).
 *
 * <p>MVP 단계에서는 {@code eventCount} / {@code successRate} / {@code lastCollectedAt} 집계 컬럼이 없어
 * dummy 값 (0 / 100.0 / null) 을 반환한다. 실제 집계는 후속 작업에서 events 또는 event_daily_agg 기반.
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
        String description = category.getDescription();
        return new EventCategorySummaryResponse(
                category.getId(),
                category.getName(),
                description,
                description,
                toKind(category.getType()),
                propertyCount,
                0L,
                100.0,
                null);
    }

    private static String toKind(EventCategoryType type) {
        return type == EventCategoryType.DEFAULT ? "BUILTIN" : "CUSTOM";
    }
}
