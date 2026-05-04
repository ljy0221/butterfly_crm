package com.b101.butterfly.segment.domain.condition;

import java.util.List;

/**
 * EVENT 조건 노드. category는 event_categories의 PK({@code id}, Long)를 참조한다.
 *
 * <p>schemaVersion 4부터 categoryId(Long)로 통일 — 이전 v3는 category(name) 사용했으나
 * 이름 변경에 따른 segment 깨짐 + 매 빌드/매칭 시 name → ID lookup 비용 + 캐시 키 단위
 * 일관성을 위해 ID로 정규화. 사용자 UX는 frontend에서 카테고리 dropdown 선택 시 ID 전달.
 */
public record EventNode(
        Long categoryId,
        List<EventFilter> filters,
        Period period,
        Integer minCount
) implements ConditionNode {

    public EventNode {
        if (minCount == null) {
            minCount = 1;
        }
    }
}
