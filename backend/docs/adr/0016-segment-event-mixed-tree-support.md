---
status: accepted
date: 2026-05-06
decision-makers: 역할 3 (segment 엔진)
consulted: 역할 5 (frontend)
amends: ADR-0006, ADR-0015
---

# ADR-0016: EVENT mixed 조건 트리 지원

## Context and Problem Statement

ADR-0006 당시 v1 정책은 EVENT 조건을 단독 트리에서만 허용하고,
EVENT가 TAG/USER/DEVICE 조건과 섞이면 `SEGMENT_400_EVENT_MIXED_NOT_SUPPORTED_V1`로 거절하는 것이었다.
`event_daily_agg`가 `log_date, project_id, category_id, user_id` 단위 집계라 EVENT property filter를 처리할 수 없고,
mixed OR 쿼리의 성능 예측이 어렵다는 점이 주된 이유였다.

2026-05-06 기준 프론트 세그먼트 빌더는 깊이 3 안에서 EVENT/TAG/USER/DEVICE leaf를 같은 그룹에 자연스럽게 추가할 수 있다.
운영 시작 전 mixed 조건이 실제 타겟팅 시나리오에 필요하다는 판단에 따라, EVENT + TAG/USER/DEVICE mixed 트리를 우선 지원한다.

## Decision

EVENT leaf를 `ConditionTreeToSqlConverter`에서 SQL 서브쿼리로 변환한다.

필터가 없는 EVENT는 기존 집계 테이블인 `event_daily_agg`를 사용한다.

```sql
EXISTS (
  SELECT 1
  FROM event_daily_agg e
  WHERE e.user_id = u.user_id
    AND e.project_id = :projectId
    AND e.category_id = :eventCategoryIdN
    -- period 조건
  GROUP BY e.user_id
  HAVING SUM(e.event_count) >= :eventMinCountN
)
```

필터가 있는 EVENT는 `event_daily_agg`에 property 컬럼이 없으므로 raw `events.event_data` JSON 조건을 사용한다.

```sql
EXISTS (
  SELECT 1
  FROM events ev
  WHERE ev.user_id = u.user_id
    AND ev.project_id = :projectId
    AND ev.category_id = :eventCategoryIdN
    AND JSON_UNQUOTE(JSON_EXTRACT(ev.event_data, :eventFilterPathN_M)) = :eventFilterValN_M
    -- occurred_at period 조건
  GROUP BY ev.user_id
  HAVING COUNT(*) >= :eventMinCountN
)
```

트리의 AND/OR 구조는 기존 converter의 재귀 조립 방식을 그대로 따른다. filter 없는 EVENT 단독 트리는 기존처럼
`EventAggReader`를 사용하고, mixed 트리, filtered EVENT, TAG/USER/DEVICE-only 트리는
`UserDirectoryReader`가 실행하는 단일 SQL 경로를 사용한다.

실시간 이벤트 매칭 캐시는 filter 없는 EVENT-only 세그먼트만 포함한다. filtered EVENT와 mixed 트리는
raw event property와 다른 도메인 조건을 함께 평가해야 하므로 rebuild SQL 경로에서 정확한 멤버십을 만든다.

## Scope

- 지원: EVENT + TAG/USER/DEVICE mixed AND/OR 트리
- 지원: EVENT leaf 여러 개, 중첩 그룹 내 EVENT
- 지원: `Period.Relative`, `Period.Absolute`, `Period.Cumulative`
- 지원: `EventNode.filters[]`의 EQ/NEQ/IN/CONTAINS/GT/LT/GTE/LTE
- 유지: `EventNode.categoryId` 사용
- 제한: EVENT 노드는 한 트리당 최대 5개

## Consequences

### 긍정

- 프론트에서 만들 수 있는 mixed 조건 트리를 백엔드가 실제로 평가할 수 있다.
- 필터 없는 EVENT는 기존 집계를 재사용하고, 필터가 있는 경우에만 raw events를 조회한다.
- 사용자가 입력한 EVENT property filter를 무시하지 않고 실제 결과에 반영한다.

### 부정 / 한계

- OR mixed 트리는 데이터 분포에 따라 옵티마이저 계획이 흔들릴 수 있다.
- 필터가 있는 EVENT는 raw `events`를 읽으므로 필터 없는 EVENT보다 무겁다.
- category 삭제로 생기는 dangling `categoryId`는 별도 정책이 필요하다.

## Validation

- `ConditionTreeToSqlConverterTest`: relative/absolute/cumulative EVENT SQL, raw event filter SQL, nested mixed OR 검증
- `PreviewServiceTest`: mixed 트리와 filtered EVENT-only SQL 경로 검증
- `SegmentBuildJobTest`: mixed 트리와 filtered EVENT-only 빌드 경로 검증
- `ActiveEventSegmentCacheTest`, `SegmentEventMatchServiceTest`: filtered EVENT를 실시간 EventAggReader 경로에서 제외하는지 검증

## Related

- [ADR-0006](0006-segment-condition-spec-v2.md)
- [ADR-0015](0015-segment-event-node-categoryid.md)
