# Customer 모듈 API (역할2: 고객/이벤트/프로필)

담당 테이블: `users`, `devices`, `events`, `events_category`, `user_tags`

---

## 1. SDK API (API Key 인증)

클라이언트 앱/웹에서 직접 호출하는 API. 빠른 응답이 필수이며, 무거운 처리는 큐를 통해 워커에서 수행.

| # | Method | URL | 설명 | 인증 | 비고 |
|---|--------|-----|------|------|------|
| 18 | POST | `/api/v1/users/identify` | 유저 식별/생성 | API Key | 로그인 시 호출 |
| 19 | POST | `/api/v1/devices` | 디바이스 등록/토큰 갱신 | API Key | FCM 토큰 등록 |
| 20 | POST | `/api/v1/events` | 이벤트 수집 (단건) | API Key | 서버가 `event_uuid` 부여 후 Kafka `events.collected` 발행, 즉시 응답 |
| 21 | POST | `/api/v1/events/batch` | 이벤트 수집 (배치) | API Key | 각 이벤트에 `event_uuid` 부여 후 `events.collected`로 발행, 즉시 응답 |

> 이벤트 수집 흐름 전체 결정(흐름 A: API → Kafka → worker → DB)과 토픽 명명 근거는 [ADR-0011](../adr/0011-event-collection-flow.md) 참조.
>
> **멱등 키 처리**: API 서버가 수신 즉시 `UUID.randomUUID()`로 `event_uuid`를 부여하고 payload에 포함시켜 Kafka에 publish. Consumer는 `events.event_uuid` UNIQUE 인덱스로 중복 메시지를 차단함. 이로써 Kafka at-least-once 시 발생하는 consumer 측 중복(offset commit 실패, rebalancing, 재처리)이 모두 자동 차단됨. 단, 클라이언트 SDK 네트워크 timeout 재시도로 인한 중복은 막지 못함 (별도 ADR로 처리 예정).
>
> **Kafka partition key**: `(projectId, userId)` 조합으로 설정 — 같은 유저 이벤트는 단일 consumer에서 순차 처리되어 `event_daily_agg` row-level lock contention 회피.

### POST /api/v1/users/identify

```json
// Request
{
  "email": "customer@example.com",
  "deviceId": 42
}

// Response
{
  "userId": 1001,
  "isNew": false
}
```

### POST /api/v1/devices

```json
// Request
{
  "userId": 1001,
  "platform": "ANDROID",
  "pushToken": "fcm_token_abc123...",
  "timezone": "Asia/Seoul",
  "language": "ko",
  "appVersion": "2.1.0",
  "osVersion": "14"
}

// Response
{
  "deviceId": 42,
  "tokenValid": true
}
```

### POST /api/v1/events

```json
// Request
{
  "userId": 1001,
  "deviceId": 42,
  "eventCategory": "purchase_complete",
  "occurredAt": "2026-04-24T14:32:10.123",
  "eventData": {
    "amount": 35000,
    "item": "운동화"
  }
}

// Response (즉시 응답, 실제 DB 적재는 워커)
{
  "eventId": "evt_01HXYZ...",
  "acceptedAt": "2026-04-24T14:32:10.456",
  "schemaVersion": 1
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| userId | Long | ✅ | 유저 PK (현재 ERD 기준 BIGINT NOT NULL) |
| deviceId | Long | ✅ | 디바이스 PK |
| eventCategory | String | ✅ | 카테고리 이름. 서버가 `event_categories.name`으로 lookup → 없으면 404 `CATEGORY_NOT_FOUND` |
| occurredAt | ISO 8601 | ✅ | 이벤트 발생 시각 |
| eventData | Object | — | 카테고리별 스키마에 따른 부가 데이터 (JSON object) |

**에러 응답**

| HTTP | code | 상황 |
|---|---|---|
| 400 | VALIDATION_FAILED | 필수 필드 누락, 포맷 오류 |
| 404 | CATEGORY_NOT_FOUND | 등록되지 않은 `eventCategory` |
| 500 | INTERNAL_ERROR | Kafka publish 실패 등 서버 오류 |

---

## 2. 유저 관리 (Management API, JWT 인증)

관리 콘솔에서 고객 데이터를 조회·관리하는 API.

| # | Method | URL | 설명 | 최소 권한 |
|---|--------|-----|------|----------|
| 22 | GET | `/api/projects/{projectId}/users` | 유저 목록 | VIEWER |
| 23 | GET | `/api/projects/{projectId}/users/{userId}` | 유저 상세 프로필 | VIEWER |
| 24 | PUT | `/api/projects/{projectId}/users/{userId}` | 유저 정보 수정 | EDITOR |
| 25 | DELETE | `/api/projects/{projectId}/users/{userId}` | 유저 삭제 | ADMIN |
| 26 | GET | `/api/projects/{projectId}/users/{userId}/devices` | 유저 디바이스 목록 | VIEWER |
| 27 | GET | `/api/projects/{projectId}/users/{userId}/events` | 유저별 이벤트 이력 | VIEWER |
| 28 | GET | `/api/projects/{projectId}/events` | 이벤트 전체 목록 | VIEWER |

### GET /api/projects/{projectId}/users/{userId}

```json
// Response — 유저 상세 프로필 (태그, 디바이스, 최근 이벤트 포함)
{
  "userId": 1001,
  "email": "customer@example.com",
  "pushOptIn": true,
  "tags": [
    { "tagKey": "region", "tagValue": "서울" },
    { "tagKey": "grade", "tagValue": "VIP" }
  ],
  "devices": [
    {
      "deviceId": 42,
      "platform": "ANDROID",
      "tokenValid": true,
      "lastActiveAt": "2026-04-19T15:30:00"
    }
  ],
  "recentEvents": [
    {
      "eventName": "purchase_complete",
      "eventData": "{\"amount\": 35000}",
      "createdAt": "2026-04-19T14:00:00"
    }
  ],
  "createdAt": "2026-01-15T09:00:00"
}
```

---

## 3. 유저 태그 관리

| # | Method | URL | 설명 | 최소 권한 |
|---|--------|-----|------|----------|
| 29 | GET | `/api/projects/{projectId}/users/{userId}/tags` | 유저 태그 조회 | VIEWER |
| 30 | POST | `/api/projects/{projectId}/users/{userId}/tags` | 태그 추가 | EDITOR |
| 31 | PUT | `/api/projects/{projectId}/users/{userId}/tags/{tagId}` | 태그 수정 | EDITOR |
| 32 | DELETE | `/api/projects/{projectId}/users/{userId}/tags/{tagId}` | 태그 삭제 | EDITOR |

### POST /api/projects/{projectId}/users/{userId}/tags

```json
// Request
{
  "tagKey": "region",
  "tagValue": "서울"
}
```

---

## 4. 이벤트 카테고리(태그 정의) 관리

| # | Method | URL | 설명 | 최소 권한 |
|---|--------|-----|------|----------|
| 33 | GET | `/api/projects/{projectId}/event-categories` | 태그 정의 목록 | VIEWER |
| 34 | POST | `/api/projects/{projectId}/event-categories` | 태그 정의 생성 | ADMIN |
| 35 | PUT | `/api/projects/{projectId}/event-categories/{categoryId}` | 태그 정의 수정 | ADMIN |
| 36 | DELETE | `/api/projects/{projectId}/event-categories/{categoryId}` | 태그 정의 삭제 | ADMIN |

### POST /api/projects/{projectId}/event-categories

```json
// Request
{
  "tagKey": "region",
  "displayName": "거주지역",
  "tagType": "STRING"
}
```
