package com.b101.butterfly.reporting.reconciliation.dto;

import java.util.List;

/**
 * 정합성 검증 트리거 API 응답 (#22, #23 — S14P31B101-187).
 *
 * <p>운영자가 트리거 호출로 즉시 검증하고 결과를 한눈에 받기 위한 요약. 상세 row는
 * {@code GET /api/v1/reconciliations} (#24)로 다시 조회한다 — 본 응답에 row 자체를 모두 담지
 * 않는다 (회차 수가 많을 수 있음).
 *
 * <p>특수 케이스:
 * <ul>
 *   <li>{@code checked} = 0 → 캠페인/회차가 존재하지 않거나 검증 대상이 없음
 *       (단일 회차 트리거는 회차 미존재 시 404, 캠페인 트리거는 회차 0개여도 200)</li>
 *   <li>{@code consistent + inconsistent = checked} 항상 성립</li>
 * </ul>
 */
public record ReconciliationTriggerResponse(
        int checked,
        int consistent,
        int inconsistent,
        List<Long> reconIds
) {
    public static ReconciliationTriggerResponse from(List<ReconciliationItem> items) {
        int consistent = (int) items.stream().filter(ReconciliationItem::consistent).count();
        return new ReconciliationTriggerResponse(
                items.size(),
                consistent,
                items.size() - consistent,
                items.stream().map(ReconciliationItem::reconId).toList()
        );
    }
}
