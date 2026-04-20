# Campaign 모듈 API (역할4: 캠페인)

담당 테이블: `campaigns`

---

## 캠페인 관리

| # | Method | URL | 설명 | 최소 권한 | 비고 |
|---|--------|-----|------|----------|------|
| 44 | POST | `/api/projects/{projectId}/campaigns` | 캠페인 생성 | EDITOR | 상태: DRAFT |
| 45 | GET | `/api/projects/{projectId}/campaigns` | 캠페인 목록 | VIEWER | 필터: status, type, channel |
| 46 | GET | `/api/projects/{projectId}/campaigns/{campaignId}` | 캠페인 상세 | VIEWER | |
| 47 | PUT | `/api/projects/{projectId}/campaigns/{campaignId}` | 캠페인 수정 | EDITOR | DRAFT 상태에서만 |
| 48 | DELETE | `/api/projects/{projectId}/campaigns/{campaignId}` | 캠페인 삭제 | EDITOR | DRAFT 상태에서만 |
| 49 | POST | `/api/projects/{projectId}/campaigns/{campaignId}/send` | 즉시 발송 | ADMIN | 감사 로그 기록 |
| 50 | POST | `/api/projects/{projectId}/campaigns/{campaignId}/schedule` | 예약 발송 | ADMIN | 감사 로그 기록 |
| 51 | POST | `/api/projects/{projectId}/campaigns/{campaignId}/cancel` | 발송 취소 | ADMIN | 감사 로그 기록 |

### POST /api/projects/{projectId}/campaigns

```json
// Request
{
  "name": "봄 세일 알림",
  "segmentId": 1,
  "channel": "PUSH",
  "type": "IMMEDIATE",
  "title": "봄맞이 세일 시작!",
  "body": "지금 바로 확인하세요. 최대 50% 할인!",
  "imageUrl": "https://cdn.example.com/spring-sale.png",
  "clickAction": "https://app.example.com/sale",
  "data": {
    "couponCode": "SPRING2026"
  }
}

// Response
{
  "campaignId": 1,
  "name": "봄 세일 알림",
  "status": "DRAFT",
  "segmentId": 1,
  "channel": "PUSH",
  "type": "IMMEDIATE",
  "title": "봄맞이 세일 시작!",
  "body": "지금 바로 확인하세요. 최대 50% 할인!",
  "imageUrl": "https://cdn.example.com/spring-sale.png",
  "clickAction": "https://app.example.com/sale",
  "data": { "couponCode": "SPRING2026" },
  "version": 1,
  "createdAt": "2026-04-20T10:00:00"
}
```

### POST /api/projects/{projectId}/campaigns/{campaignId}/schedule

```json
// Request
{
  "scheduledAt": "2026-04-25T09:00:00"
}
```

### GET /api/projects/{projectId}/campaigns 필터 파라미터

```
?status=DRAFT&type=IMMEDIATE&channel=PUSH&page=0&size=20
```

---

## 캠페인 상태 흐름

```
DRAFT → SCHEDULED → SENDING → COMPLETED
  ↑         |                     |
  └─ cancel ┘         FAILED ←───┘
```

| 전환 | 트리거 | 비고 |
|------|--------|------|
| DRAFT → SENDING | `/send` 호출 | 즉시 발송 |
| DRAFT → SCHEDULED | `/schedule` 호출 | 예약 발송 |
| SCHEDULED → SENDING | 스케줄러가 시각 도래 시 | 워커에서 처리 |
| SCHEDULED → DRAFT | `/cancel` 호출 | 예약 취소 |
| SENDING → COMPLETED | 모든 발송 완료 시 | 워커에서 처리 |
| SENDING → FAILED | 발송 실패 시 | 워커에서 처리 |

---

## Enum 값

### channel

| 값 | 설명 |
|----|------|
| PUSH | 앱/웹 푸시 (MVP) |
| EMAIL | 이메일 (향후 확장) |

### type

| 값 | 설명 |
|----|------|
| IMMEDIATE | 즉시 발송 |
| SCHEDULED | 예약 발송 |
| RECURRING | 반복 발송 (cron 기반) |
