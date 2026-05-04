# Segment Domain API Contract (Sprint 5)

**Backend Liaison**: Claude Backend Liaison Agent
**Last Updated**: 2026-05-01
**Status**: ✅ Sprint 5 실 8개 엔드포인트 확정, 미구현 3개 표시

---

## 공통 규약

### ApiResponse 래퍼
모든 응답은 다음 구조로 래핑됨:

**성공 (HTTP 200, 201, 202):**
\\\json
{
  "success": true,
  "data": { /* 실제 응답 본문 */ },
  "error": null
}
\\\

**실패 (HTTP 400~501):**
\\\json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SEGMENT_400_NAME_LENGTH",
    "message": "세그먼트 이름은 1자 이상 30자 이하여야 합니다."
  }
}
\\\

### 인증 헤더
- **X-Member-Id**: \Long\ (임시 헤더, 추후 @AuthenticationPrincipal로 교체)

### 페이지네이션
\\\json
{
  "content": [ /* 아이템 배열 */ ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
\\\

---

## 조건 트리 데이터 모델

### GroupNode (루트)
논리 그룹으로 조건들을 AND/OR 결합.

\\\	ypescript
{
  "logic": "and" | "or",
  "conditions": ConditionNode[]
}
\\\

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| logic | "and" \| "or" | ✅ | 조건 결합 방식 |
| conditions | ConditionNode[] | ✅ | 1개 이상의 조건 노드 배열 |

---

### ConditionNode (Polymorphic)

sealed interface로 5가지 구체 타입 지원.

#### 1. EventNode (이벤트 필터)

\\\	ypescript
{
  "type": "event",
  "category": "purchase" | "click" | "view",
  "filters": EventFilter[],
  "period": Period,
  "minCount": 1
}
\\\

**EventFilter:**
\\\	ypescript
{
  "key": "productId" | "price",
  "op": "eq" | "neq" | "gt" | "lt" | "gte" | "lte" | "in" | "contains",
  "value": JsonNode
}
\\\

**Period (Polymorphic):**

- **Relative** - 최근 N일
  \\\	ypescript
  {
    "type": "relative",
    "days": 7
  }
  \\\

- **Absolute** - 특정 날짜 범위
  \\\	ypescript
  {
    "type": "absolute",
    "startDate": "2026-01-01",
    "endDate": "2026-05-01"
  }
  \\\

- **Cumulative** - 전체 기간
  \\\	ypescript
  {
    "type": "cumulative"
  }
  \\\

#### 2. UserNode (사용자 속성)

\\\	ypescript
{
  "type": "user",
  "key": "birthDate" | "joinedAt",
  "op": "eq" | "neq" | "gt" | "lt" | "gte" | "lte" | "in" | "contains",
  "value": JsonNode
}
\\\

#### 3. TagNode (태그)

\\\	ypescript
{
  "type": "tag",
  "key": "tagName",
  "op": "eq" | "neq" | "in" | "contains",
  "value": JsonNode
}
\\\

#### 4. DeviceNode (디바이스)

\\\	ypescript
{
  "type": "device",
  "key": "os" | "deviceType",
  "op": "eq" | "neq" | "in",
  "value": JsonNode
}
\\\

#### 5. GroupNode (중첩)

\\\	ypescript
{
  "logic": "and" | "or",
  "conditions": ConditionNode[]
}
\\\

---

### Operator Enum

eq, neq, gt, lt, gte, lte, in, contains

---

## CRUD 엔드포인트

### 1. POST /api/v1/projects/{projectId}/segments

**권한**: EDITOR 이상

**요청**:
\\\json
{
  "name": "string (1~30자, 동일 프로젝트 내 중복 불가)",
  "description": "string (0~100자)",
  "conditionTree": GroupNode
}
\\\

**응답** (HTTP 201):
\\\json
{
  "segmentId": 123,
  "name": "고가 구매자",
  "buildStatus": "PENDING",
  "estimatedCount": 0,
  "estimatedAt": null,
  "lastBuiltAt": null,
  "schemaVersion": 3,
  "createdBy": 456,
  "createdAt": "2026-05-01T12:00:00"
}
\\\

**에러**:
- SEGMENT_400_NAME_LENGTH (400)
- SEGMENT_400_NAME_DUPLICATED (400)
- SEGMENT_400_DESCRIPTION_LENGTH (400)
- SEGMENT_400_EMPTY_CONDITIONS (400)
- SEGMENT_400_INVALID_CONDITION_TREE (400)
- SEGMENT_501_EVENT_NOT_READY (501)

---

### 2. GET /api/v1/projects/{projectId}/segments

**권한**: VIEWER 이상

**응답** (HTTP 200): PageResponse<SegmentSummaryResponse>

\\\json
{
  "content": [
    {
      "segmentId": 123,
      "name": "고가 구매자",
      "description": "설명",
      "estimatedCount": 5234,
      "buildStatus": "READY",
      "createdBy": {"memberId": 456, "name": "John Doe"},
      "createdAt": "2026-05-01T12:00:00",
      "updatedAt": "2026-05-01T14:00:00",
      "linkedCampaignCount": 2,
      "favorited": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3
}
\\\

---

### 3. GET /api/v1/projects/{projectId}/segments/{segmentId}

**권한**: VIEWER 이상

**응답** (HTTP 200): SegmentDetailResponse

모든 필드 포함 (conditionTree, linkedCampaigns 등)

**에러**: SEGMENT_404_NOT_FOUND (404)

---

### 4. PUT /api/v1/projects/{projectId}/segments/{segmentId}

**권한**: EDITOR 이상

**요청**: CreateSegmentRequest와 동일

**응답** (HTTP 200): SegmentDetailResponse

**제약사항**:
- buildStatus = BUILDING → SEGMENT_409_BUILD_IN_PROGRESS (409)
- 진행 중 캠페인 연결 → SEGMENT_409_LINKED_ACTIVE_CAMPAIGN (409)

---

### 5. DELETE /api/v1/projects/{projectId}/segments/{segmentId}

**권한**: EDITOR 이상

**응답** (HTTP 200): { success: true, data: null, error: null }

**에러**:
- SEGMENT_404_NOT_FOUND (404)
- SEGMENT_409_LINKED_ACTIVE_CAMPAIGN (409)

---

## Preview 엔드포인트

### POST /api/v1/projects/{projectId}/segments/preview

**권한**: EDITOR 이상

**요청**:
\\\json
{
  "conditionTree": GroupNode
}
\\\

**응답** (HTTP 200):
\\\json
{
  "estimatedCount": 5234,
  "method": "HLL" | "EXACT",
  "estimatedAt": "2026-05-01T12:00:00"
}
\\\

**에러**:
- SEGMENT_400_INVALID_CONDITION_TREE (400)
- SEGMENT_501_EVENT_NOT_READY (501)

---

## Favorites 엔드포인트

### GET /api/v1/projects/{projectId}/segments/favorites

**권한**: VIEWER 이상

**응답** (HTTP 200):
\\\json
{
  "favorites": [
    {
      "segmentId": 123,
      "name": "고가 구매자",
      "estimatedCount": 5234,
      "favoritedAt": "2026-05-01T12:00:00"
    }
  ],
  "recentlyUsed": [
    {
      "segmentId": 456,
      "name": "신규 가입자",
      "estimatedCount": 12345,
      "lastUsedAt": "2026-04-30T10:00:00"
    }
  ]
}
\\\

---

### POST /api/v1/projects/{projectId}/segments/{segmentId}/favorite

**권한**: VIEWER 이상

**응답** (HTTP 200): { success: true, data: null, error: null }

---

### DELETE /api/v1/projects/{projectId}/segments/{segmentId}/favorite

**권한**: VIEWER 이상

**응답** (HTTP 200): { success: true, data: null, error: null }

---

## 미구현 엔드포인트 (Phase 6~8)

### ❌ GET /api/v1/projects/{projectId}/segments/{segmentId}/users

**상태**: Phase 8 미구현

**목표**: SegmentMember 페이지 + event.users JOIN

**응답**: PageResponse<SegmentUserResponse>

현재 Mock: 빈 리스트

---

### ❌ POST /api/v1/projects/{projectId}/segments/{segmentId}/rebuild

**상태**: Phase 6 미구현

**목표**: buildStatus CAS + 큐 publish

**응답** (202 Accepted):
\\\json
{
  "segmentId": 123,
  "buildStatus": "BUILDING",
  "queuedAt": "2026-05-01T12:30:00"
}
\\\

현재 Mock: 항상 BUILDING 반환

---

### ❌ GET /api/v1/projects/{projectId}/segments/{segmentId}/campaigns

**상태**: Phase 7 미구현

**목표**: LinkedCampaignChecker.listLinkedCampaigns 변환

**응답**:
\\\json
{
  "active": [{"campaignId": 789, "name": "봄 세일", "campaignStatus": "ACTIVE"}],
  "paused": [],
  "draft": [],
  "ended": [],
  "totalCount": 1
}
\\\

현재 Mock: 빈 배열 반환

---

## 에러 코드

| 코드 | HTTP | 메시지 |
|------|------|--------|
| SEGMENT_400_NAME_LENGTH | 400 | 세그먼트 이름은 1자 이상 30자 이하여야 합니다. |
| SEGMENT_400_NAME_DUPLICATED | 400 | 동일 프로젝트 내에 같은 이름의 세그먼트가 이미 존재합니다. |
| SEGMENT_400_DESCRIPTION_LENGTH | 400 | 세그먼트 설명은 100자 이하여야 합니다. |
| SEGMENT_400_EMPTY_CONDITIONS | 400 | 조건은 1개 이상이어야 합니다. |
| SEGMENT_400_INVALID_CONDITION_TREE | 400 | 조건 트리 스키마가 올바르지 않습니다. |
| SEGMENT_404_NOT_FOUND | 404 | 세그먼트를 찾을 수 없습니다. |
| SEGMENT_409_LINKED_ACTIVE_CAMPAIGN | 409 | 진행 중인 캠페인에 연결되어 수정 또는 삭제할 수 없습니다. |
| SEGMENT_409_BUILD_IN_PROGRESS | 409 | 세그먼트 빌드가 진행 중입니다. |
| SEGMENT_501_EVENT_NOT_READY | 501 | EVENT 조건은 event 모듈 합류 전까지 미리보기에 사용할 수 없습니다. |

---

## 권한 매트릭스

| 작업 | 권한 요구 |
|------|---------|
| CREATE | EDITOR↑ |
| READ_LIST | VIEWER↑ |
| READ_DETAIL | VIEWER↑ |
| UPDATE | EDITOR↑ |
| DELETE | EDITOR↑ |
| PREVIEW | EDITOR↑ |
| REBUILD | EDITOR↑ |
| FAVORITE_* | VIEWER↑ |

---

**End of Contract**
