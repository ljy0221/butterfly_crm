---
status: accepted
date: 2026-04-21
decision-makers: 역할 3 (세그먼트 엔진)
consulted: 역할 6 (분석/통합 검증)
---

# ADR-0001: 세그먼트 조건 트리를 JSON 컬럼으로 저장

## Context and Problem Statement

requirements 3-3은 세그먼트 조건의 **AND/OR 중첩**을 필수 기능으로 명시한다.

초안 ERD의 `segment_rules`는 `(rule_id, segment_id, rule_type, field, operator, value)` 의 평탄한 리스트로
AND 단순 조합만 표현 가능하다. 즉 **`(서울 AND VIP) OR (신규 가입)`** 같은 현실적 조건을 저장할 방법이 없다.

세그먼트별 성과(분석 API 61)와 세그먼트 미리보기(API 42)가 모두 이 구조를 읽어 해석하므로,
저장 포맷을 확정하지 않으면 역할 3(세그먼트 엔진)과 역할 6(분석)이 동시에 착수할 수 없다.

## Decision Drivers

- AND/OR 중첩 깊이가 제한 없이 가능해야 한다 (UI에서 그룹 안에 그룹을 넣을 수 있어야 함)
- 세그먼트 미리보기 응답 시간이 수 초 내외여야 한다 (requirements 3-3)
- 역할 3이 SSAFY 기간 내에 구현 가능해야 한다 (분산 시스템 복잡도 최소화 — 제약 7-3)
- 추후 새로운 rule_type(EVENT, DEVICE, USER, ...) 추가 시 스키마 마이그레이션 비용이 낮아야 한다

## Considered Options

### Option A — 자기참조 테이블 (부모-자식 FK)

`segment_rules`에 `parent_rule_id`, `logical_op(AND/OR)` 컬럼을 추가하고 트리를 평탄화 저장.

- 👍 관계형으로 조회/검증/인덱싱이 가능
- 👎 트리 로딩이 N+1 쿼리 위험, 재귀 CTE 필요
- 👎 UI에서 "규칙 1개 저장"을 여러 row INSERT로 쪼개야 해서 원자성 관리가 복잡

### Option B — JSON 컬럼 (조건 트리 통째 저장)

`segments` 테이블에 `condition_tree JSON NOT NULL` 컬럼 추가. 서비스 계층에서 역직렬화하여 해석.

- 👍 저장/조회 시 단일 row — 원자성과 로딩이 단순
- 👍 트리 깊이 무제한
- 👍 새로운 rule_type 추가가 스키마 변경 없이 가능 (JSON 키 확장)
- 👎 DB 레벨 검증 불가 — 애플리케이션 레이어에서 스키마 검증 필요
- 👎 세그먼트별 "특정 규칙으로 필터"가 SQL만으로는 어렵다 (MySQL 8 JSON 함수로 우회 가능하나 인덱스 이용이 제한적)

### Option C — 별도 조건 언어 DSL 파일

세그먼트 조건을 커스텀 DSL 문자열로 저장 (예: `region='서울' AND (grade IN ('VIP','VVIP') OR events.purchase_complete >= 3)`).

- 👍 가장 유연
- 👎 DSL 파서/컴파일러 구현 비용 과다 — MVP 일정 초과
- 👎 UI(시각 빌더) ↔ DSL 왕복 변환 로직 복잡

## Decision Outcome

**선택: Option B (JSON 컬럼)**.

이유:
- MVP 일정 안에서 가장 확실히 동작하는 선택지
- UI 빌더의 트리 상태 ↔ JSON 구조가 1:1 매핑되어 양방향 변환이 단순
- 인덱스 제약은 실제 운영 단계에서 고빈도 세그먼트용 `SegmentSnapshot`(requirements 3-3 선택 기능)으로 보완 가능
- 스키마 확장성 — 이벤트/디바이스/속성 조건 타입이 늘어도 마이그레이션 불필요

### 구현 가이드

```sql
ALTER TABLE segments
  ADD COLUMN condition_tree JSON NOT NULL COMMENT '조건 트리 (AND/OR 중첩)';
```

기존 `segment_rules` 테이블은 조회 최적화(고빈도 필터)가 필요해지기 전까지는 유지하지 않는다.
필요 시 역정규화 테이블로 되살릴 수 있다.

트리 예시:

```json
{
  "op": "AND",
  "children": [
    { "op": "LEAF", "ruleType": "TAG", "field": "region", "operator": "EQ", "value": "서울" },
    {
      "op": "OR",
      "children": [
        { "op": "LEAF", "ruleType": "TAG", "field": "grade", "operator": "IN", "value": "VIP,VVIP" },
        { "op": "LEAF", "ruleType": "EVENT", "field": "purchase_complete", "operator": "GTE", "value": "3" }
      ]
    }
  ]
}
```

애플리케이션 레이어는 다음 스키마 계약을 유지한다:

- `op` ∈ `{AND, OR, LEAF}` — LEAF는 `children` 없이 `ruleType/field/operator/value` 보유
- 내부 노드는 `children`을 1개 이상 보유
- 최대 트리 깊이 제한은 서비스 계층에서 둔다 (초안: 10)

### Consequences

**좋은 점**
- 역할 3은 ORM 관점에서 `Segment.conditionTree`를 POJO 트리로 다루면 된다
- 역할 6은 세그먼트 UI/미리보기 구현과 무관하게 `segment_metric_daily`만 보고 집계한다
- 향후 Event 기반 조건이 늘어도 ERD 변경 없음

**나쁜 점 / 주의**
- JSON 스키마 변경 시 기존 데이터 마이그레이션 규칙을 ADR로 남겨야 한다
- "이 속성으로 필터된 세그먼트 목록" 류 조회는 JSON 스캔이라 느릴 수 있음 — 필요 시 역정규화 테이블 추가
- DB 레벨 검증이 없으므로 **서비스 계층에서 JSON Schema 검증을 반드시 수행**한다

## More Information

- 제안서 §3-3: [backend/docs/proposals/01-erd-api-review.md](../proposals/01-erd-api-review.md)
- requirements 3-3: [backend/docs/requirements.md](../requirements.md)
- Segment API: [backend/docs/api/03-segment.md](../api/03-segment.md)
