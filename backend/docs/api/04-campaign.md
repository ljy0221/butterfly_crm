# Campaign 모듈 API (역할4: 캠페인)

담당 테이블: `campaigns`, `campaign_executions`

> ADR-0002 (정의/실행 분리), ADR-0011 (Kafka 발송 파이프라인) 참고.
> 본 명세는 캠페인 상태 전이를 동사 endpoint(`/send`, `/schedule`, `/cancel`)가 아닌
> **`PATCH /status` sub-resource**로 통합한 신규 설계를 반영한다 (RESTful URL 원칙).

---

## 캠페인 관리

| # | Method | URL | 설명 | 최소 권한 | 비고 |
|---|--------|-----|------|----------|------|
| 44 | POST   | `/api/projects/{projectId}/campaigns`                  | 캠페인 생성    | EDITOR | 상태: DRAFT |
| 45 | GET    | `/api/projects/{projectId}/campaigns`                  | 캠페인 목록    | VIEWER | 페이지네이션 |
| 46 | GET    | `/api/projects/{projectId}/campaigns/{campaignId}`     | 캠페인 상세    | VIEWER | |
| 47 | PATCH  | `/api/projects/{projectId}/campaigns/{campaignId}`     | 일반 필드 수정 | EDITOR | DRAFT 상태에서만 |
| 48 | DELETE | `/api/projects/{projectId}/campaigns/{campaignId}`     | 캠페인 삭제   | EDITOR | DRAFT 상태에서만, soft delete |
| 49 | PATCH  | `/api/projects/{projectId}/campaigns/{campaignId}/status` | 상태 전이 (활성화/일시정지/재개/종료) | EDITOR | 도메인 state machine 검증 |

> 기존 `POST /send`, `/schedule`, `/cancel`은 모두 #49 `PATCH .../status`로 통합.
> SCHEDULED/RECURRING/AUTOMATION 발송은 후속 작업.

### 공통 헤더

| 헤더 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `Idempotency-Key` | UUID | Optional | 클라이언트 자동 재시도 안전화. POST/PATCH/DELETE에 권장. 같은 키 재요청 시 동일 결과 보장 (Stripe 패턴, 후속 Redis 캐싱 예정) |

이중 방어 (현재 시점):
1. State Machine validation — 잘못된 전이는 도메인이 거부
2. DB UNIQUE — `campaign_executions.idempotency_key` UNIQUE로 이중 발송 차단

---

### POST /api/projects/{projectId}/campaigns

```json
// Request
{
  "messageTemplateId": 789,
  "segmentId": 100,
  "campaignName": "봄 세일 알림",
  "campaignDescription": "VIP 고객 대상 봄 세일 안내"
}

// Response 201 Created
{
  "campaignId": 1
}
```

- `segmentId`가 `null`이면 프로젝트 전체 사용자 발송
- 채널/제목/바디/이미지는 `messageTemplateId`가 결정 (캠페인이 별도로 보유 X — ADR-0002)

---

### GET /api/projects/{projectId}/campaigns

페이지네이션 파라미터: `?page=0&size=10&sort=createdAt,desc`

```json
// Response 200 OK
{
  "content": [
    {
      "campaignId": 1,
      "campaignName": "봄 세일 알림",
      "channel": "PUSH",
      "campaignStatus": "DRAFT",
      "createdAt": "2026-05-02T10:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

---

### GET /api/projects/{projectId}/campaigns/{campaignId}

```json
// Response 200 OK
{
  "campaignId": 1,
  "projectId": 1,
  "messageTemplateId": 789,
  "segmentId": 100,
  "memberId": 10,
  "campaignName": "봄 세일 알림",
  "campaignDescription": "VIP 고객 대상 봄 세일 안내",
  "campaignType": "ONE_TIME",
  "sendMode": "IMMEDIATE",
  "channel": "PUSH",
  "campaignStatus": "DRAFT",
  "createdAt": "2026-05-02T10:00:00",
  "updatedAt": "2026-05-02T10:00:00"
}
```

---

### PATCH /api/projects/{projectId}/campaigns/{campaignId}

DRAFT 상태에서만 일반 필드(`campaignName`, `campaignDescription`) 수정 허용.

```json
// Request
{
  "campaignName": "수정된 캠페인",
  "campaignDescription": "수정된 설명"
}

// Response 200 OK
{ "campaignId": 1 }
```

오류:
- `409 NOT_MODIFIABLE` — DRAFT가 아닌 상태에서 시도
- `404 CAMPAIGN_NOT_FOUND` — 다른 프로젝트의 캠페인 (정보 노출 회피)

---

### DELETE /api/projects/{projectId}/campaigns/{campaignId}

DRAFT 상태에서만 soft delete. 응답 본문 없음.

```
204 No Content
```

오류:
- `409 NOT_MODIFIABLE`
- `404 CAMPAIGN_NOT_FOUND`

---

### PATCH /api/projects/{projectId}/campaigns/{campaignId}/status

캠페인 상태 전이용 단일 진입점. 본문에 `status`만 허용.

```json
// Request
{
  "status": "ACTIVE"
}

// Response 200 OK
{
  "campaignId": 1,
  "campaignStatus": "ACTIVE"
}
```

#### 동작 분기

| 현재 상태 | target | 동작 |
|---|---|---|
| `DRAFT`  | `ACTIVE` | DRAFT → ACTIVE 전이 + `CampaignExecution(runRound=1, READY)` 생성 + Kafka publish + Execution을 SEND로 전이 |
| `ACTIVE` | `PAUSED` | 도메인 `pause()` |
| `ACTIVE` | `ENDED`  | 도메인 `complete()` |
| `PAUSED` | `ACTIVE` | 도메인 `resume()` (Execution 새로 생성 X — 같은 회차 이어가기) |
| `PAUSED` | `ENDED`  | 도메인 `complete()` |

#### 전이 매트릭스 (불허용 전이는 모두 `409 INVALID_STATUS_TRANSITION`)

| from \ to | DRAFT | ACTIVE | PAUSED | ENDED |
|---|---|---|---|---|
| `DRAFT`  | ❌ | ✅ | ❌ | ❌ |
| `ACTIVE` | ❌ | ❌ | ✅ | ✅ |
| `PAUSED` | ❌ | ✅ | ❌ | ✅ |
| `ENDED`  | ❌ | ❌ | ❌ | ❌ |

---

## Kafka 메시지 스키마 — `campaign.send.requested`

발송 활성화 시 publish되는 가벼운 지시서. Worker(notification 모듈)가 이를 consume해서 fan-out 책임.
Partition key: `projectId` 문자열.

```json
{
  "schemaVersion": 1,
  "executionId": 123,
  "campaignId": 456,
  "projectId": 1,
  "templateId": 789,
  "segmentId": 100,
  "channel": "PUSH",
  "idempotencyKey": "exec-123",
  "publishedAt": "2026-05-02T12:00:00Z"
}
```

`segmentId`가 `null`이면 프로젝트 전체 사용자.
`idempotencyKey`는 `exec-{executionId}` 형식으로 일관성 보장.

---

## Enum 값

### `CampaignStatus`

| 값 | 설명 |
|---|---|
| `DRAFT`  | 초기 상태. 수정/삭제 가능 |
| `ACTIVE` | 발송 진행 중 (Execution 회차 진행 중) |
| `PAUSED` | 일시정지 (반복/자동화 캠페인용 — MVP에선 별도 유스케이스 X) |
| `ENDED`  | 종착 상태 |

### `CampaignType`

| 값 | 설명 |
|---|---|
| `ONE_TIME`   | 1회 발송 (즉시/예약/반복) |
| `AUTOMATION` | 이벤트 트리거 자동화 |

### `SendMode`

| 값 | 설명 |
|---|---|
| `IMMEDIATE` | 즉시 발송 (MVP 스코프) |
| `SCHEDULED` | 예약 발송 (후속) |
| `RECURRING` | 반복 발송 (cron, 후속) |

### `Channel` (템플릿이 소유)

| 값 | 설명 |
|---|---|
| `PUSH` | 앱/웹 푸시 |
| `EMAIL` | 이메일 |
| `SMS` | SMS |
| `KAKAO` | 카카오톡 |
| `WEBHOOK` | 웹훅 |

### `CampaignExecutionStatus`

| 값 | 설명 |
|---|---|
| `READY`   | Execution 생성 직후, publish 전 |
| `SEND`    | Kafka publish 완료 (worker 큐에 적재됨) |
| `SUCCESS` | (후속) 모든 발송 성공 |
| `FAIL`    | (후속) 발송 실패 |

---

## 변경 이력

- 2026-05-02: ADR-0002 정의/실행 분리 + RESTful sub-resource 패턴 적용. 동사 endpoint(`/send`, `/schedule`, `/cancel`) 모두 `PATCH .../status`로 통합. 요청/응답 schema는 template FK 기반으로 갱신.
