# Segment 모듈 API (butterfly-segment)

담당 테이블: `segments`, `segment_members`, `segment_favorites`, `segment_snapshots`, `segment_snapshot_users`

> **변경 이력**
> - 2026-04-27: ERD 개정 반영 — `segment_rules` 테이블 폐기, `condition_tree` JSON 단일 소스로 변경 (ADR-0001).
>   빌드 상태 추적, 즐겨찾기, 캠페인 연결 조회, 모수 재계산 API 추가.
> - 2026-04-30: API 경로에 `v1` prefix 적용 ([S14P31B101-163]). `/api/projects/...` → `/api/v1/projects/...`.
> - 2026-05-06: EVENT 노드는 `categoryId`를 사용하고, EVENT + TAG/USER/DEVICE mixed 트리를 지원한다 (ADR-0015, ADR-0016).

---

## 1. 세그먼트 CRUD

| # | Method | URL | 설명 | 최소 권한 | 비고 |
|---|--------|-----|------|----------|------|
| 37 | POST   | `/api/v1/projects/{projectId}/segments`                       | 세그먼트 생성              | EDITOR | `condition_tree`로 생성. 생성 직후 `build_status=PENDING` |
| 38 | GET    | `/api/v1/projects/{projectId}/segments`                       | 세그먼트 목록              | VIEWER | 검색/필터/정렬/페이지네이션 |
| 39 | GET    | `/api/v1/projects/{projectId}/segments/{segmentId}`           | 세그먼트 상세              | VIEWER | 조건 트리 + 빌드 상태 + 사용 중 캠페인 |
| 40 | PUT    | `/api/v1/projects/{projectId}/segments/{segmentId}`           | 세그먼트 수정              | EDITOR | 진행 중(READY/SEND) 캠페인 연결 시 거절 또는 강한 경고 |
| 41 | DELETE | `/api/v1/projects/{projectId}/segments/{segmentId}`           | 세그먼트 삭제 (soft)       | EDITOR | 진행 중 캠페인 연결 시 거절 |
| 42 | POST   | `/api/v1/projects/{projectId}/segments/preview`               | 예상 모수 미리보기         | EDITOR | 저장 없이 조건만 평가 (HLL 추정) |
| 43 | GET    | `/api/v1/projects/{projectId}/segments/{segmentId}/users`     | 대상 유저 목록             | VIEWER | 페이지네이션 (P1) |
| 44 | POST   | `/api/v1/projects/{projectId}/segments/{segmentId}/rebuild`   | 모수 재계산/멤버 재빌드     | EDITOR | 비동기, `build_status=BUILDING` 전환 |
| 45 | GET    | `/api/v1/projects/{projectId}/segments/{segmentId}/campaigns` | 이 세그먼트를 쓰는 캠페인 목록 | VIEWER | 캠페인 상태별 그룹화 |

## 2. 즐겨찾기 (P1)

| # | Method | URL | 설명 | 최소 권한 |
|---|--------|-----|------|----------|
| 46 | GET    | `/api/v1/projects/{projectId}/segments/favorites`                  | 자주 쓰는 세그먼트 목록 | VIEWER |
| 47 | POST   | `/api/v1/projects/{projectId}/segments/{segmentId}/favorite`       | 즐겨찾기 등록           | VIEWER |
| 48 | DELETE | `/api/v1/projects/{projectId}/segments/{segmentId}/favorite`       | 즐겨찾기 해제           | VIEWER |

---

## 3. 요청/응답 예시

### 37. POST `/api/v1/projects/{projectId}/segments`

**Request**
```json
{
  "name": "최근 구매 3회 이상 서울 유저",
  "description": "최근 30일 내 구매 이벤트가 3회 이상이고 서울 태그를 가진 유저",
  "conditionTree": {
    "logic": "and",
    "conditions": [
      {
        "type": "event",
        "categoryId": 42,
        "filters": [
          { "key": "color", "op": "eq", "value": "검정" },
          { "key": "size",  "op": "eq", "value": "260" }
        ],
        "period":   { "type": "relative", "days": 30 },
        "minCount": 3
      },
      {
        "type": "tag",
        "key":  "region",
        "op":   "eq",
        "value": "서울"
      },
      {
        "type": "user",
        "key":  "birthYear",
        "op":   "lt",
        "value": 2000
      }
    ]
  }
}
```

**Response 201**
```json
{
  "segmentId": 1024,
  "name": "검정 구두 260 구매 유저",
  "description": "최근 30일 내 ...",
  "buildStatus": "PENDING",
  "estimatedCount": null,
  "estimatedAt": null,
  "lastBuiltAt": null,
  "schemaVersion": 4,
  "createdBy": 17,
  "createdAt": "2026-04-27T10:00:00"
}
```

**제약**
- `name`: 1~30자, 프로젝트 내 중복 불가 (UNIQUE `(project_id, name, is_deleted)`)
- `description`: 0~100자
- `conditionTree.conditions`: 최소 1개 이상

---

### 38. GET `/api/v1/projects/{projectId}/segments`

**Query Parameters**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `q`           | string | 세그먼트명 검색 (LIKE) |
| `createdBy`   | long   | 생성자 member_id 필터 |
| `createdFrom` | date   | 생성일 범위 시작 |
| `createdTo`   | date   | 생성일 범위 끝 |
| `buildStatus` | enum   | `PENDING/BUILDING/READY/FAILED` |
| `sort`        | string | `createdAt`, `estimatedCount`, `lastBuiltAt` (default: `updatedAt`) |
| `order`       | string | `asc`, `desc` (default: `desc`) |
| `page`        | int    | 0-base |
| `size`        | int    | default 20 |

**Response 200**
```json
{
  "content": [
    {
      "segmentId": 1024,
      "name": "검정 구두 260 구매 유저",
      "description": "...",
      "estimatedCount": 1820,
      "buildStatus": "READY",
      "createdBy": { "memberId": 17, "name": "남정연" },
      "createdAt": "2026-04-27T10:00:00",
      "updatedAt": "2026-04-27T10:30:00",
      "linkedCampaignCount": 3,
      "favorited": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

---

### 39. GET `/api/v1/projects/{projectId}/segments/{segmentId}`

**Response 200**
```json
{
  "segmentId": 1024,
  "name": "검정 구두 260 구매 유저",
  "description": "...",
  "conditionTree": { "logic": "and", "conditions": [ ... ] },
  "schemaVersion": 3,
  "estimatedCount": 1820,
  "estimatedAt": "2026-04-27T10:30:00",
  "lastBuiltAt": "2026-04-27T10:32:14",
  "buildStatus": "READY",
  "createdBy": { "memberId": 17, "name": "남정연" },
  "createdAt": "2026-04-27T10:00:00",
  "updatedAt": "2026-04-27T10:30:00",
  "linkedCampaigns": [
    { "campaignId": 501, "name": "5월 봄 시즌", "campaignStatus": "ACTIVE" },
    { "campaignId": 502, "name": "VIP 사은품",   "campaignStatus": "DRAFT" }
  ],
  "favorited": true
}
```

---

### 40. PUT `/api/v1/projects/{projectId}/segments/{segmentId}`

수정 요청 본문은 생성과 동일.

**거절 응답 (409 CONFLICT)** — 진행 중 캠페인 연결 시
```json
{
  "code": "SEGMENT_409_LINKED_ACTIVE_CAMPAIGN",
  "message": "이 세그먼트를 사용 중인 활성 캠페인이 있어 수정할 수 없습니다.",
  "linkedCampaigns": [
    { "campaignId": 501, "name": "5월 봄 시즌", "campaignStatus": "ACTIVE" }
  ]
}
```

---

### 42. POST `/api/v1/projects/{projectId}/segments/preview`

저장 없이 `conditionTree`만 받아서 예상 모수를 즉시 반환.

**Request**
```json
{
  "conditionTree": {
    "logic": "and",
    "conditions": [
      { "type": "tag", "key": "region", "op": "eq", "value": "서울" }
    ]
  }
}
```

**Response 200**
```json
{
  "estimatedCount": 5120,
  "method": "HLL",
  "estimatedAt": "2026-04-27T10:35:00"
}
```

> `method`: `HLL`(HyperLogLog 근사) 또는 `EXACT`(실측). 100만 유저 스케일에서는 `HLL` 기본.

---

### 43. GET `/api/v1/projects/{projectId}/segments/{segmentId}/users` (P1)

세그먼트 매칭 유저 목록 (`segment_members` 기반).

**Query Parameters**: `page` (기본 0), `size` (기본 20)

**구현 노트** ([-119], Phase 8-1):
- 만료된 멤버(`expires_at <= now`)는 응답에서 제외 — 만료 배치 cron 사이 시점도 안전
- 다른 프로젝트의 segmentId 접근 시 404 (정보 노출 회피)
- `lastActiveAt`은 v1에서 항상 `null` — ERD에 `last_active_at` 컬럼 없음. v1.5/v2에서 정밀화
- ADR-0012 anonymous placeholder user(`__anonymous__`)는 응답에서 제외 (v1 known-user only)
- 미존재 user (placeholder/타프로젝트)는 `userId`/`matchedAt`만 채우고 `externalId`/`email`은 `null`

**Response 200**
```json
{
  "content": [
    {
      "userId": 9001,
      "externalId": "ext-9001",
      "email": "user@example.com",
      "lastActiveAt": null,
      "matchedAt": "2026-04-27T10:32:10.123"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1820
}
```

---

### 44. POST `/api/v1/projects/{projectId}/segments/{segmentId}/rebuild`

비동기 재빌드 트리거. `build_status`가 `BUILDING`으로 전환되고 큐에 작업이 적재됨.

**Response 202 Accepted**
```json
{
  "segmentId": 1024,
  "buildStatus": "BUILDING",
  "queuedAt": "2026-04-27T10:40:00"
}
```

빌드 진행 상태 폴링은 39번(상세 조회)으로.

---

### 45. GET `/api/v1/projects/{projectId}/segments/{segmentId}/campaigns`

이 세그먼트를 사용 중인 캠페인 목록. 수정/삭제 가능 여부 판단용.

**Response 200**
```json
{
  "active":   [ { "campaignId": 501, "name": "5월 봄 시즌", "campaignStatus": "ACTIVE" } ],
  "paused":   [],
  "draft":    [ { "campaignId": 502, "name": "VIP 사은품",   "campaignStatus": "DRAFT" } ],
  "ended":    [ { "campaignId": 480, "name": "4월 봄맞이",   "campaignStatus": "ENDED"  } ],
  "totalCount": 3
}
```

---

### 46. GET `/api/v1/projects/{projectId}/segments/favorites` (P1)

**Response 200** — 즐겨찾기 우선, 그다음 최근 캠페인에 사용된 순

```json
{
  "favorites": [
    { "segmentId": 1024, "name": "검정 구두 260 구매 유저", "estimatedCount": 1820, "favoritedAt": "2026-04-26T09:00:00" }
  ],
  "recentlyUsed": [
    { "segmentId":  990, "name": "휴면 30일", "estimatedCount": 12400, "lastUsedAt": "2026-04-25T18:30:00" }
  ]
}
```

---

## 4. condition_tree 스키마

ADR-0001 참조. 단일 진실 소스(JSON).

### 노드 타입

```jsonc
// 그룹 노드 (재귀 가능)
{
  "logic": "and" | "or",
  "conditions": [ <조건 노드 또는 그룹 노드> ]
}
```

### 조건 노드 — `type`별

| `type` | 의미 | 추가 필드 |
|--------|------|---------|
| `event`  | 이벤트 발생 조건             | `categoryId`, `filters[]`, `period`, `minCount` |
| `tag`    | `user_tags` 태그 필터        | `key`, `op`, `value` |
| `user`   | `users` 컬럼 필터            | `key`, `op`, `value` (예: `birthDate`, `pushOptIn`) |
| `device` | `devices` 컬럼 필터          | `key`, `op`, `value` (예: `platform`) |

> `EventNode.filters`가 비어 있으면 `event_daily_agg` 집계를 사용하고, 필터가 있으면 raw `events.event_data` JSON 조건으로 평가한다.

### `period` (이벤트 조건 한정)

| `type` | 필드 | 설명 |
|--------|------|------|
| `relative`   | `days`              | 최근 N일 |
| `absolute`   | `startDate`, `endDate` | YYYY-MM-DD |
| `cumulative` | (없음)              | 전체 기간 |

### 연산자 (`op`)

| 연산자 | 설명 |
|--------|------|
| `eq`       | 같음 |
| `neq`      | 같지 않음 |
| `gt`       | 초과 |
| `lt`       | 미만 |
| `gte`      | 이상 |
| `lte`      | 이하 |
| `in`       | 포함 (배열, 예: `["VIP","VVIP"]`) |
| `contains` | 문자열 포함 |

---

## 5. 빌드 상태 (`build_status`)

| 값 | 의미 |
|---|---|
| `PENDING`  | 생성/수정 직후, 빌드 대기 중 |
| `BUILDING` | events 스캔 + `segment_members` 적재 중 |
| `READY`    | 빌드 완료, 캠페인에서 사용 가능 |
| `FAILED`   | 빌드 실패 — 재시도(`POST /rebuild`) 필요 |

---

## 6. 권한 매트릭스

| 행위 | VIEWER | EDITOR | OWNER |
|------|:------:|:------:|:-----:|
| 목록/상세/유저 목록/즐겨찾기 조회 | ✅ | ✅ | ✅ |
| 즐겨찾기 등록/해제 | ✅ | ✅ | ✅ |
| 생성/수정/삭제/미리보기/재빌드 | ❌ | ✅ | ✅ |

---

## 7. 주요 에러 코드

| 코드 | HTTP | 설명 |
|------|:----:|------|
| `SEGMENT_400_NAME_LENGTH`           | 400 | 이름이 1~30자 범위를 벗어남 |
| `SEGMENT_400_NAME_DUPLICATED`       | 400 | 동일 프로젝트 내 이름 중복 |
| `SEGMENT_400_DESCRIPTION_LENGTH`    | 400 | 설명이 100자 초과 |
| `SEGMENT_400_EMPTY_CONDITIONS`      | 400 | 조건이 1개 미만 |
| `SEGMENT_400_INVALID_CONDITION_TREE`| 400 | condition_tree 스키마 위반 |
| `SEGMENT_404_NOT_FOUND`             | 404 | 세그먼트 미존재 또는 삭제됨 |
| `SEGMENT_409_LINKED_ACTIVE_CAMPAIGN`| 409 | 진행 중 캠페인에 연결되어 수정/삭제 불가 |
| `SEGMENT_409_BUILD_IN_PROGRESS`     | 409 | 빌드 진행 중 — 재요청 시 충돌 |
