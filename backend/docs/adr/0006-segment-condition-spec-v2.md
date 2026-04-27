---
status: accepted
date: 2026-04-27
decision-makers: 역할 3 (세그먼트 엔진)
consulted: 역할 2 (이벤트 수집), 역할 4 (캠페인), 역할 6 (분석)
supersedes: ADR-0001
---

# ADR-0006: 세그먼트 조건 트리 스펙 v2 — 요구명세 개정 반영

## Context and Problem Statement

[ADR-0001](0001-segment-condition-tree-json.md)은 "조건 트리를 JSON 컬럼으로 저장한다"는 큰 방향만 확정했다. 이후 [proposals/03](../proposals/03-segment-condition-spec-revision.md)이 EVENT 집계, STATIC/DYNAMIC 하이브리드, `event_daily_counts` 등을 제안했지만 합의 대기 상태로 머물렀다.

2026-04-27 요구명세서가 전면 개정되고 ERD도 재작성되면서, 기존 두 문서의 결정사항 일부가 **그대로 유효**, 일부가 **새 결정으로 교체**, 일부가 **폐기**되었다. 어느 문서를 진실로 봐야 할지 모호한 상태이므로 본 ADR로 v2 스펙을 확정한다.

## Decision Drivers

- 새 요구명세서: 이벤트 카테고리 + 속성 필터(`eventData`) + 기간(절대/상대) + 발생 횟수 + 사용자 속성을 모두 표현해야 함
- 새 ERD: `segments.build_status`(PENDING/BUILDING/READY/FAILED), `event_daily_agg`(customer 모듈 소유), `event_categories`/`event_schemas` 분리
- 100만 유저 / 일 1천만 이벤트 스케일에서 미리보기 응답성 + 발송 정확성 동시 보장
- 후속 작업([S14P31B101-25](https://ssafy.atlassian.net/browse/S14P31B101-25), [-26](https://ssafy.atlassian.net/browse/S14P31B101-26), [-24](https://ssafy.atlassian.net/browse/S14P31B101-24))이 동일 기준으로 구현 가능해야 함

## Decision Outcome

### 1. 저장 — `segments.condition_tree JSON` 단일 소스 (ADR-0001 계승)

- 컬럼명은 `condition_tree`로 유지 (proposals/03이 제안한 `condition_json` 폐기).
- `segment_rules` 정규화 테이블은 도입하지 않음 (ADR-0001 결정 계승).
- ANTLR DSL 도입 거절 유지 (ADR-0001 결정 계승).

### 2. JSON 스키마 v2 — 노드 표기 변경

```jsonc
{
  "logic": "and" | "or",
  "conditions": [ <조건 노드 또는 그룹 노드> ]
}
```

조건 노드 — `type`별 (4종):

| `type` | 추가 필드 |
|--------|---------|
| `event`  | `category`, `filters[]`, `period`, `minCount` |
| `tag`    | `key`, `op`, `value` |
| `user`   | `key`, `op`, `value` |
| `device` | `key`, `op`, `value` |

기간 (`period`, event 한정) — 3종:

| `type` | 필드 | 설명 |
|--------|------|------|
| `relative`   | `days` | 최근 N일 |
| `absolute`   | `startDate`, `endDate` | YYYY-MM-DD 범위 |
| `cumulative` | (없음) | 전체 기간 |

연산자 (`op`) — 8종: `eq`, `neq`, `gt`, `lt`, `gte`, `lte`, `in`, `contains`.

상세 스키마는 [03-segment.md §4](../api/03-segment.md)에 단일 진실 소스로 유지. 필드 이름 변경/추가 시 ADR 갱신 + `schemaVersion` bump.

**ADR-0001 v1과의 차이**: `op`/`children`/`ruleType`/`LEAF` 표기를 `logic`/`conditions`/`type`(+ Discriminated Union)로 교체. proposals/03이 제안한 `operator` 키 대신 `logic` 키 채택(MySQL JSON 키 가독성, AND/OR가 SQL 연산자가 아닌 트리 결합자임을 명시).

### 3. 평가 패턴 — Materialized 단일화 (STATIC/DYNAMIC 하이브리드 폐기)

| 시점 | 경로 | 응답성 | 정확도 |
|---|---|:---:|:---:|
| 미리보기 | HyperLogLog 추정 | ~수ms | ~99% |
| 빌드 후 모수 | `segment_members COUNT` | ~수십ms | 100% |
| 캠페인 발송 | `segment_members SELECT` (청크) | 청크 페이지네이션 | 100% (스냅샷) |

- `segments.evaluation_type ENUM('DYNAMIC','STATIC')`은 ERD에서 제거됨 — Materialized 패턴 단일화.
- `segments.build_status`로 라이프사이클 추적: `PENDING` → `BUILDING` → `READY` / `FAILED`.
- 초기 빌드는 events 스캔으로 `segment_members` 적재. 증분 매칭은 Kafka 컨슈머 C가 신규 이벤트만 처리(events 재스캔 없음).
- 카운트 조건(`minCount`)은 events 직접 SUM 쿼리(ADR-0001 §4-3 결정 계승). `segment_match_counters` 카운터 테이블 도입 거절 유지.

### 4. 이벤트 집계 테이블 — `event_daily_agg` (customer 모듈 소유)

- proposals/03이 제안한 `event_daily_counts`는 **새 ERD의 `event_daily_agg`로 대체**되었다 (customer 모듈 소유, 세그먼트는 읽기 전용).
- PK: `(log_date, project_id, category_id, user_id)`.
- 필드: `event_count`, `first_at`, `last_at`, `updated_at` (ms 단위 타임스탬프 보존).
- 적재 방식과 SLA는 customer 모듈 책임. 세그먼트 엔진은 보장된 정합성으로만 읽음.

### 5. Redis 캐시 정책

| 키 | TTL | 용도 | 무효화 트리거 |
|---|:---:|---|---|
| `seg:active:{projectId}:{categoryId}` | 1분 | 컨슈머 C 활성 세그먼트 캐시 | 활성/비활성/삭제 시 Pub/Sub publish |
| `segment:hll:{segmentId}` | 24시간 | 미리보기 모수 추정 | `condition_tree` 수정 시 즉시 DEL |
| `event:dedup:{eventId}` | 10분 | at-least-once 중복 방지 | TTL 만료 |

원칙: TTL 우선, 즉시성 필요 시만 명시적 invalidation.

### 6. 만료 / 인덱스

- `segment_members(expires_at)` 인덱스 필수 (`idx_sm_expires`, [newerd.md](../newerd.md)에 반영됨).
- 매일 새벽 `DELETE FROM segment_members WHERE expires_at < NOW()` 배치.
- 누적 조건(`expires_at IS NULL`)은 자동 제외.

### 7. 가드레일

- proposals/03이 제안한 "depth 3 / EVENT 3개 / 노드 10개 / timeWindowDays 30일" 4가드 세트는 **참고 권고로만 채택**(고정 강제 X). 운영 중 실측 후 강제 제약으로 격상할지 재결정.
- UI 빌더에서 4단 이상 중첩 시 경고 표시(소프트 가드).

## Consequences

### 좋은 점
- 후속 작업([S14P31B101-24](https://ssafy.atlassian.net/browse/S14P31B101-24)/[-25](https://ssafy.atlassian.net/browse/S14P31B101-25)/[-26](https://ssafy.atlassian.net/browse/S14P31B101-26))이 단일 ADR을 보고 구현 시작 가능.
- 프런트 빌더는 Discriminated Union으로 TypeScript 타입 1:1 매핑 가능.
- STATIC/DYNAMIC 분기 사라져 운영 복잡도 감소.

### 주의
- ADR-0001 시점에 작성된 코드/문서가 있다면 v1 키(`op`/`children`/`ruleType`)를 사용하지 않는지 확인 필요. 현 시점 구현 전이므로 비용 낮음.
- `event_daily_agg`의 적재 SLA 합의는 customer 모듈과 별도로 진행해야 함 (본 ADR 범위 외).
- proposals/03의 `condition_json` 컬럼명 제안과 충돌하므로, proposals/03 헤더에 "ADR-0006으로 대체됨" 배너를 둔다.

## More Information

- 선행 ADR (superseded): [ADR-0001 세그먼트 조건 트리를 JSON 컬럼으로 저장](0001-segment-condition-tree-json.md)
- 선행 제안 (outdated): [proposals/03 세그먼트 조건 트리 스펙 개정](../proposals/03-segment-condition-spec-revision.md)
- 구현 문서: [api/03-segment.md](../api/03-segment.md)
- ERD: [newerd.md](../newerd.md)
- 관련 작업: [S14P31B101-51](https://ssafy.atlassian.net/browse/S14P31B101-51) (저장 전략 + 캐시 정책 정리)
