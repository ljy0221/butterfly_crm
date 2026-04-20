# Delivery 모듈 API (역할5: 발송/로그)

담당 테이블: `notification_logs`

---

## 1. 발송 로그 조회 (Management API, JWT 인증)

| # | Method | URL | 설명 | 최소 권한 |
|---|--------|-----|------|----------|
| 52 | GET | `/api/projects/{projectId}/campaigns/{campaignId}/logs` | 캠페인 발송 로그 목록 | VIEWER |
| 53 | GET | `/api/projects/{projectId}/logs/{logId}` | 발송 로그 상세 | VIEWER |
| 54 | GET | `/api/projects/{projectId}/users/{userId}/logs` | 유저별 발송 이력 | VIEWER |

### GET /api/projects/{projectId}/campaigns/{campaignId}/logs

```
?status=SENT&page=0&size=20
```

```json
// Response
{
  "content": [
    {
      "logId": 1,
      "campaignId": 1,
      "userId": 1001,
      "deviceId": 42,
      "status": "DELIVERED",
      "sentAt": "2026-04-20T09:01:00",
      "deliveredAt": "2026-04-20T09:01:03",
      "clickedAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2340
}
```

### GET /api/projects/{projectId}/logs/{logId}

```json
// Response
{
  "logId": 1,
  "projectId": 1,
  "campaignId": 1,
  "userId": 1001,
  "deviceId": 42,
  "status": "DELIVERED",
  "errorCode": null,
  "errorMessage": null,
  "sentAt": "2026-04-20T09:01:00",
  "deliveredAt": "2026-04-20T09:01:03",
  "clickedAt": null,
  "createdAt": "2026-04-20T09:01:00"
}
```

---

## 2. 발송 추적 콜백 (SDK API, API Key 인증)

기기에서 알림 도달/클릭 시 호출하는 콜백 API.

| # | Method | URL | 설명 | 인증 | 비고 |
|---|--------|-----|------|------|------|
| 55 | POST | `/sdk/v1/notifications/{logId}/delivered` | 도달 확인 콜백 | API Key | 기기에서 호출 |
| 56 | POST | `/sdk/v1/notifications/{logId}/clicked` | 클릭 확인 콜백 | API Key | 기기에서 호출 |

### POST /sdk/v1/notifications/{logId}/delivered

```json
// Request
{
  "deliveredAt": "2026-04-20T09:01:03"
}

// Response
{
  "accepted": true
}
```

### POST /sdk/v1/notifications/{logId}/clicked

```json
// Request
{
  "clickedAt": "2026-04-20T09:05:30"
}

// Response
{
  "accepted": true
}
```

---

## 발송 상태 (status)

| 값 | 설명 |
|----|------|
| SENT | FCM 발송 완료 |
| DELIVERED | 기기 도달 확인 |
| CLICKED | 알림 클릭 확인 |
| FAILED | 발송 실패 |

### 상태 흐름

```
SENT → DELIVERED → CLICKED
  |
  └→ FAILED
```
