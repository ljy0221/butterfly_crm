---
status: accepted
date: 2026-05-04
decision-makers: 역할 3 (segment 엔진)
consulted: 역할 2 (event 수집), 역할 5 (frontend)
amends: ADR-0006
---

# ADR-0015: EventNode.category 식별자 — name(string) → categoryId(Long)

> 본 ADR은 최초 0014로 발행됐으나, 동시 작성된 이씨 [-21] events.persisted 토픽 ADR과 번호가 겹쳐 0015로 변경됨 (2026-05-06).

## Context and Problem Statement

[ADR-0006](0006-segment-condition-spec-v2.md) v2 스펙에서 `EventNode.category`는 카테고리 **이름(string)**으로 정의됐다. 사용자 입력 직관성 + JSON 가독성을 우선한 선택이었다.

Phase 6-3 ([S14P31B101-179]) 실시간 매칭 컨슈머 진입 단계에서 다음 문제가 드러났다.

1. **캐시 키 불일치**: 활성 EVENT 세그먼트 캐시 키 후보 `seg:active:{projectId}:{categoryId}`는 event 페이로드의 categoryId(Long)와 자연스럽게 매칭되지만, segment의 EventNode는 categoryName(string)을 보유 → 양방향 매핑 필요.
2. **매 빌드/매칭 시 lookup 비용**: `EventAggAdapter`가 매 호출마다 `EventCategoryRepository.findByProjectIdAndName()` 1회 조회. 빌드 1만 user × 활성 segment 10개 = 10만 회.
3. **카테고리 이름 변경 영향**: name 기준이면 카테고리가 "purchase" → "구매"로 이름 바뀌면 segment가 깨짐. ID 기준이면 영향 없음.
4. **사전 검증 불가**: 이름은 Validator에서 존재 검증을 못 하고 빌드 시점에 발견.

## Decision

`EventNode.category String` → `EventNode.categoryId Long`로 정규화한다. **schemaVersion 4** (3에서 1 증가).

### 변경 내역

| 항목 | v3 (ADR-0006 v2) | v4 (본 ADR) |
|---|---|---|
| `EventNode` 필드 | `String category` | `Long categoryId` |
| JSON 키 | `"category": "purchase"` | `"categoryId": 42` |
| Validator | name 비공백 검사 | categoryId > 0 검사 |
| `EventAggAdapter` | name → ID 변환 후 SQL | 직접 ID 사용 (변환 제거) |
| 사용자 UX | 이름 입력 | frontend dropdown에서 ID 선택 |
| schemaVersion | 3 | 4 |

### 마이그레이션 부담

현재 develop 단계, **배포 0회 + 운영 segment 데이터 0건**. 마이그레이션 스크립트 불필요.

### 모듈 영향

- **butterfly-segment**: `EventNode` record / `TypeFieldValidator` / `ConditionNodeSerializer` / `Segment` schemaVersion 기본값 / 픽스처 JSON 갱신
- **butterfly-event**: `EventAggAdapter` (categoryRepository 의존 제거 + Phase 6-3 `userMatches` override 동시에 추가)
- **butterfly-frontend**: 카테고리 dropdown — name 표시 + ID 전달 (사용자가 별도 협의)

## Consequences

### 긍정

- 캐시 키와 페이로드 단위 일치 → 매칭 컨슈머 단순
- 빌드/매칭 시 EventCategoryRepository 조회 0회 (성능)
- 카테고리 이름 변경에 segment 안전
- Validator에서 사전 검증 가능 (categoryId 존재 확인은 후속 — Phase 6-3에선 EventAggAdapter SQL이 자연 검증)

### 부정 / 한계

- JSON 가독성 ↓: `{"categoryId": 42}`는 사람이 디버깅 시 의미 모름. → audit/log 출력 시점에 name도 함께 기록하면 보완.
- 카테고리 삭제 시 dangling reference 가능. → 카테고리 거의 안 변하니 v1 운영 영향 작음. v2에서 카테고리 삭제 hook + segment 무효화 정책 별도 ADR.

## Validation

- segment 단위 테스트 (`EventNodeTest`, `TypeFieldValidatorTest`, `ConditionTreeJsonRoundTripTest`)
- event 단위 테스트 (`EventAggAdapterTest`)
- 전체 segment + event 빌드 통과

## Related

- [ADR-0006](0006-segment-condition-spec-v2.md) v2 — 본 ADR이 amend (categoryId 부분만)
- [S14P31B101-179](https://ssafy.atlassian.net/browse/S14P31B101-179) — Phase 6-3 실시간 매칭 (본 변경 포함)
