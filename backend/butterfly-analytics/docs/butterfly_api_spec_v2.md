# 📘 Butterfly CRM - 분석/감사/로깅 API 명세서

> **담당**: 이재영
> **버전**: v2.0 (2026-04-28)
> **Base URL**: `https://api.butterfly.com`
> **인증**: `Authorization: Bearer {access_token}`
> **Content-Type**: `application/json`

---

## 📝 v2.0 변경 이력

이전 버전(v1.0) 대비 변경 사항:

- ✨ **NEW**: 캠페인 회차(execution) 단위 통계 API 4개 추가
- ✨ **NEW**: 회차별 정합성 검증 API 추가
- 🔄 **CHANGED**: 모든 응답에 `messageTemplateId`, `campaignExecutionId` 필드 추가
- 🔄 **CHANGED**: `notification_logs.status` ENUM에서 `CLICKED` 제거 (오픈/클릭은 user_action_logs로 분리)
- 🔄 **CHANGED**: 감사 로그 응답에 `ipAddress` 필드 복구
- 🔄 **CHANGED**: `template_id` → `messageTemplateId`로 명칭 통일
- 🔄 **CHANGED**: `campaign_executions` 테이블 분리에 따른 stats 키 변경

---

## 📋 목차

1. [공통 규약](#공통-규약)
2. [대시보드 조회 API](#-대시보드-조회-api) (10개)
3. [캠페인 회차 API](#-캠페인-회차-api) (4개) ✨ NEW
4. [로그 조회 API](#-로그-조회-api) (4개)
5. [이벤트 수신 API](#-이벤트-수신-api) (1개)
6. [감사 조회 API](#-감사-조회-api) (2개)
7. [정합성/운영 API](#-정합성운영-api) (6개) ✨ +1
8. [내부 API](#-내부-api) (3개)
9. [에러 코드 전체 목록](#-에러-코드-전체-목록)

---

# 공통 규약

## 인증

모든 API는 JWT Access Token이 필요합니다 (내부 API 및 이벤트 수신 API 제외).

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## 권한 레벨

| Role | 설명 |
|---|---|
| `ADMIN` | CRM 운영진 (모든 프로젝트 접근) |
| `OWNER` | 프로젝트 최상위 권한 |
| `EDITOR` | 세그먼트/캠페인 관리 |
| `VIEWER` | 조회만 가능 (기본) |

## 공통 응답 포맷

### 성공 (200, 201)

```json
{
  "success": true,
  "data": { },
  "timestamp": "2026-04-28T15:30:00.000Z"
}
```

### 페이지네이션 응답

```json
{
  "success": true,
  "data": {
    "items": [],
    "pagination": {
      "page": 1,
      "size": 20,
      "totalElements": 157,
      "totalPages": 8,
      "hasNext": true
    }
  }
}
```

### 에러 응답

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "해당 프로젝트에 접근 권한이 없습니다.",
    "details": { "projectId": 123 }
  },
  "timestamp": "2026-04-28T15:30:00.000Z"
}
```

## 공통 에러 코드

| HTTP | Code | 설명 |
|---|---|---|
| 400 | `BAD_REQUEST` | 잘못된 요청 파라미터 |
| 401 | `UNAUTHORIZED` | 토큰 없음 또는 만료 |
| 403 | `FORBIDDEN` | 권한 부족 |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 409 | `CONFLICT` | 중복 또는 충돌 |
| 429 | `RATE_LIMITED` | 요청 한도 초과 |
| 500 | `INTERNAL_ERROR` | 서버 에러 |

---

# 📊 대시보드 조회 API

## 1. 프로젝트 대시보드 전체 지표 조회

프로젝트 대시보드 상단의 요약 지표를 한 번에 조회합니다.

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/projects/{projectId}/dashboard` |
| **권한** | VIEWER 이상 (해당 프로젝트 소속) |

### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| projectId | Long | ✅ | 프로젝트 ID |

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| date | String(YYYY-MM-DD) | ❌ | 오늘 | 기준일 |

### Request 예시

```http
GET /api/v1/projects/123/dashboard?date=2026-04-28
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "projectId": 123,
    "bucketDate": "2026-04-28",
    "activeUserCount": 15420,
    "newUserCount": 312,
    "churnUserCount": 89,
    "totalSent": 48201,
    "totalDelivered": 46890,
    "totalFailed": 1311,
    "totalOpen": 18234,
    "totalClick": 4521,
    "totalConvert": 892,
    "pushOpenRate": 38.89,
    "overallClickRate": 24.79,
    "overallConvertRate": 1.85,
    "activeCampaignCount": 12
  }
}
```

### Error Cases

- `403 FORBIDDEN` - 해당 프로젝트 멤버가 아님
- `404 NOT_FOUND` - 프로젝트 없음

---

## 2. 프로젝트 일별 통계 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/projects/{projectId}/stats/daily` |
| **권한** | VIEWER 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| startDate | String(YYYY-MM-DD) | ✅ | | 시작일 |
| endDate | String(YYYY-MM-DD) | ✅ | | 종료일 (최대 90일) |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "projectId": 123,
    "period": { "startDate": "2026-04-01", "endDate": "2026-04-28" },
    "items": [
      {
        "bucketDate": "2026-04-28",
        "activeUserCount": 15420,
        "totalSent": 48201,
        "totalOpen": 18234,
        "pushOpenRate": 38.89
      }
    ]
  }
}
```

---

## 3. 캠페인 성과 리포트 조회

특정 캠페인의 전체 누적 성과를 조회합니다 (모든 회차 합산).

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/campaigns/{campaignId}/stats` |
| **권한** | VIEWER 이상 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "campaignId": 4521,
    "campaignName": "4월 신상품 알림",
    "campaignType": "AUTOMATION",
    "channel": "PUSH",
    "totalExecutionCount": 12,
    "totalSent": 145000,
    "totalDelivered": 141200,
    "totalFailed": 3800,
    "totalOpen": 58000,
    "totalClick": 14500,
    "totalConvert": 2810,
    "deliveryRate": 97.38,
    "openRate": 41.07,
    "clickRate": 25.00,
    "convertRate": 1.94,
    "firstSentAt": "2026-04-01T10:00:00.000Z",
    "lastSentAt": "2026-04-28T10:00:00.000Z"
  }
}
```

> 💡 회차별 상세 성과는 [10. 회차별 통계 조회](#10-회차별-통계-조회) 참조

---

## 4. 캠페인 시간별 추이 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/campaigns/{campaignId}/stats/hourly` |
| **권한** | VIEWER 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| startHour | String(ISO-8601) | ✅ | 시작 시각 |
| endHour | String(ISO-8601) | ✅ | 종료 시각 (최대 7일 범위) |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "campaignId": 4521,
    "channel": "PUSH",
    "items": [
      {
        "bucketHour": "2026-04-28T15:00:00",
        "sentCount": 1250,
        "deliveredCount": 1218,
        "failedCount": 32,
        "openCount": 487,
        "clickCount": 121,
        "convertCount": 23
      }
    ]
  }
}
```

---

## 5. 캠페인 일별 추이 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/campaigns/{campaignId}/stats/daily` |
| **권한** | VIEWER 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| startDate | String | ✅ | 시작일 |
| endDate | String | ✅ | 종료일 |
| channel | String | ❌ | 채널 필터 (PUSH/EMAIL/SMS/WEBHOOK/KAKAO) |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "campaignId": 4521,
    "items": [
      {
        "bucketDate": "2026-04-28",
        "channel": "PUSH",
        "sentCount": 12500,
        "openCount": 4872,
        "openRate": 40.00,
        "clickRate": 24.94,
        "convertRate": 1.87
      }
    ]
  }
}
```

---

## 6. 최근 캠페인 목록 조회 (대시보드용)

요구사항: "최근 발송 목록 5개"

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/projects/{projectId}/campaigns/recent` |
| **권한** | VIEWER 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| limit | Integer | ❌ | 5 | 최대 10 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "campaignId": 4521,
        "campaignName": "4월 신상품 알림",
        "campaignStatus": "ACTIVE",
        "lastExecutionStatus": "SUCCESS",
        "channel": "PUSH",
        "sentCount": 12500,
        "openRate": 40.00,
        "lastSentAt": "2026-04-28T10:00:00.000Z"
      }
    ]
  }
}
```

> 🔄 v2 변경: `status` → `campaignStatus` + `lastExecutionStatus` 분리. campaign 상태와 실행 상태가 분리됐기 때문.

---

## 7. 템플릿 효율 리포트 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/templates/{messageTemplateId}/stats` |
| **권한** | VIEWER 이상 |

### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| messageTemplateId | Long | ✅ | 메시지 템플릿 ID |

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| startDate | String | ✅ | |
| endDate | String | ✅ | |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "messageTemplateId": 89,
    "templateName": "이벤트 종료 임박 알림",
    "channel": "PUSH",
    "period": { "startDate": "2026-04-01", "endDate": "2026-04-28" },
    "usedCampaignCount": 12,
    "sentCount": 145000,
    "openCount": 62350,
    "clickCount": 18421,
    "convertCount": 4102,
    "openRate": 43.00,
    "clickRate": 29.55,
    "convertRate": 2.83
  }
}
```

> 🔄 v2 변경: `templateId` → `messageTemplateId`로 명칭 통일

---

## 8. 성과 TOP 템플릿 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/projects/{projectId}/templates/stats/top` |
| **권한** | VIEWER 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| sortBy | String | ❌ | openRate | openRate / clickRate / convertRate |
| limit | Integer | ❌ | 10 | 최대 50 |
| startDate | String | ✅ | | |
| endDate | String | ✅ | | |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "sortBy": "openRate",
    "items": [
      {
        "messageTemplateId": 89,
        "templateName": "이벤트 종료 임박 알림",
        "channel": "PUSH",
        "sentCount": 145000,
        "openRate": 43.00,
        "clickRate": 29.55
      }
    ]
  }
}
```

---

## 9. 실패 원인 대시보드 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/projects/{projectId}/failures` |
| **권한** | EDITOR 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| startDate | String | ✅ | |
| endDate | String | ✅ | |
| channel | String | ❌ | 채널 필터 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "bucketDate": "2026-04-28",
        "channel": "PUSH",
        "errorCode": "INVALID_TOKEN",
        "failedCount": 892,
        "affectedCampaignCount": 5,
        "affectedUserCount": 892,
        "firstOccurredAt": "2026-04-28T10:00:12.000Z",
        "lastOccurredAt": "2026-04-28T14:32:45.000Z"
      }
    ]
  }
}
```

---

## 10. 실패 원인 TOP 5 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/projects/{projectId}/failures/top` |
| **권한** | EDITOR 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| date | String | ❌ | 오늘 | 기준일 |
| limit | Integer | ❌ | 5 | |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "bucketDate": "2026-04-28",
    "items": [
      { "errorCode": "INVALID_TOKEN", "failedCount": 892, "percentage": 68.03 },
      { "errorCode": "AUTH_ERROR", "failedCount": 234, "percentage": 17.85 },
      { "errorCode": "RATE_LIMIT", "failedCount": 112, "percentage": 8.54 },
      { "errorCode": "TIMEOUT", "failedCount": 45, "percentage": 3.43 },
      { "errorCode": "UNKNOWN", "failedCount": 28, "percentage": 2.13 }
    ]
  }
}
```

---

# 🔁 캠페인 회차 API ✨ NEW

> 반복 발송 캠페인의 회차별 성과를 추적하기 위한 API 그룹입니다.  
> 단발성 캠페인도 1회차로 표현됩니다.

## 11. 캠페인 회차 목록 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/campaigns/{campaignId}/executions` |
| **권한** | VIEWER 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| status | String | ❌ | | READY/SEND/SUCCESS/FAIL |
| page | Integer | ❌ | 1 | |
| size | Integer | ❌ | 20 | |
| sortBy | String | ❌ | runRound | runRound / sendStartedAt |
| order | String | ❌ | desc | asc / desc |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "campaignId": 4521,
    "items": [
      {
        "campaignExecutionId": 88123,
        "runRound": 12,
        "campaignExecutionStatus": "SUCCESS",
        "sendStartedAt": "2026-04-28T10:00:00.000Z",
        "sendCompletedAt": "2026-04-28T10:05:23.000Z",
        "targetCount": 12500,
        "sentCount": 12180,
        "failedCount": 320,
        "failReason": null
      },
      {
        "campaignExecutionId": 88011,
        "runRound": 11,
        "campaignExecutionStatus": "SUCCESS",
        "sendStartedAt": "2026-04-27T10:00:00.000Z",
        "sendCompletedAt": "2026-04-27T10:05:18.000Z",
        "targetCount": 12420,
        "sentCount": 12102,
        "failedCount": 318,
        "failReason": null
      }
    ],
    "pagination": { "page": 1, "size": 20, "totalElements": 12, "totalPages": 1 }
  }
}
```

---

## 12. 회차 상세 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/campaign-executions/{executionId}` |
| **권한** | VIEWER 이상 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "campaignExecutionId": 88123,
    "campaignId": 4521,
    "campaignName": "4월 신상품 알림",
    "runRound": 12,
    "campaignExecutionStatus": "SUCCESS",
    "sendStartedAt": "2026-04-28T10:00:00.000Z",
    "sendCompletedAt": "2026-04-28T10:05:23.000Z",
    "targetCount": 12500,
    "sentCount": 12180,
    "failedCount": 320,
    "failReason": null,
    "actionSummary": {
      "openCount": 4872,
      "clickCount": 1215,
      "convertCount": 234
    },
    "rates": {
      "deliveryRate": 97.44,
      "openRate": 40.00,
      "clickRate": 24.94,
      "convertRate": 1.87
    }
  }
}
```

---

## 13. 회차별 통계 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/campaign-executions/{executionId}/stats` |
| **권한** | VIEWER 이상 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "campaignExecutionId": 88123,
    "campaignId": 4521,
    "runRound": 12,
    "channel": "PUSH",
    "targetCount": 12500,
    "sentCount": 12180,
    "deliveredCount": 11980,
    "failedCount": 320,
    "openCount": 4872,
    "clickCount": 1215,
    "convertCount": 234,
    "deliveryRate": 97.44,
    "openRate": 40.00,
    "clickRate": 24.94,
    "convertRate": 1.87
  }
}
```

---

## 14. 회차별 발송 로그 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/campaign-executions/{executionId}/notification-logs` |
| **권한** | EDITOR 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| status | String | ❌ | PENDING/SENT/DELIVERED/FAILED |
| page | Integer | ❌ | |
| size | Integer | ❌ | 최대 100 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "campaignExecutionId": 88123,
    "items": [
      {
        "logId": 98234521,
        "userId": 78901,
        "deviceId": 23456,
        "channel": "PUSH",
        "status": "DELIVERED",
        "sentAt": "2026-04-28T10:00:05.123Z",
        "deliveredAt": "2026-04-28T10:00:07.456Z"
      }
    ],
    "pagination": { "page": 1, "size": 20, "totalElements": 12500, "totalPages": 625 }
  }
}
```

---

# 📝 로그 조회 API

## 15. 발송 로그 목록 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/notification-logs` |
| **권한** | EDITOR 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| projectId | Long | ✅ | | 프로젝트 ID |
| campaignId | Long | ❌ | | 특정 캠페인 필터 |
| campaignExecutionId | Long | ❌ | | 특정 회차 필터 ✨ NEW |
| messageTemplateId | Long | ❌ | | 특정 템플릿 필터 ✨ NEW |
| status | String | ❌ | | PENDING/SENT/DELIVERED/FAILED |
| channel | String | ❌ | | 채널 필터 |
| errorCode | String | ❌ | | 에러 코드 필터 |
| startDate | String | ❌ | | |
| endDate | String | ❌ | | |
| page | Integer | ❌ | 1 | |
| size | Integer | ❌ | 20 | 최대 100 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "logId": 98234521,
        "projectId": 123,
        "campaignId": 4521,
        "campaignExecutionId": 88123,
        "messageTemplateId": 89,
        "userId": 78901,
        "deviceId": 23456,
        "channel": "PUSH",
        "status": "DELIVERED",
        "errorCode": null,
        "sentAt": "2026-04-28T10:00:05.123Z",
        "deliveredAt": "2026-04-28T10:00:07.456Z",
        "createdAt": "2026-04-28T10:00:04.000Z"
      }
    ],
    "pagination": {
      "page": 1, "size": 20, "totalElements": 12500, "totalPages": 625, "hasNext": true
    }
  }
}
```

> 🔄 v2 변경: `campaignExecutionId`, `messageTemplateId` 필드 추가. status에서 `CLICKED` 제거.

---

## 16. 발송 로그 상세 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/notification-logs/{logId}` |
| **권한** | EDITOR 이상 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "logId": 98234521,
    "projectId": 123,
    "campaignId": 4521,
    "campaignExecutionId": 88123,
    "messageTemplateId": 89,
    "userId": 78901,
    "deviceId": 23456,
    "channel": "PUSH",
    "status": "DELIVERED",
    "errorCode": null,
    "errorMessage": null,
    "sentAt": "2026-04-28T10:00:05.123Z",
    "deliveredAt": "2026-04-28T10:00:07.456Z",
    "createdAt": "2026-04-28T10:00:04.000Z",
    "actionSummary": {
      "openCount": 1,
      "clickCount": 2,
      "convertCount": 0
    }
  }
}
```

---

## 17. 특정 발송의 사용자 반응 목록 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/notification-logs/{logId}/actions` |
| **권한** | EDITOR 이상 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "logId": 98234521,
    "items": [
      {
        "actionId": 55123098,
        "actionType": "OPEN",
        "actionTarget": null,
        "userAgent": "Mozilla/5.0 (iPhone)...",
        "actionAt": "2026-04-28T10:02:15.000Z"
      },
      {
        "actionId": 55123205,
        "actionType": "CLICK",
        "actionTarget": "https://shop.example.com/promo",
        "userAgent": "Mozilla/5.0 (iPhone)...",
        "actionAt": "2026-04-28T10:02:32.000Z"
      }
    ]
  }
}
```

---

## 18. 사용자 반응 로그 목록 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/user-action-logs` |
| **권한** | EDITOR 이상 |

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| projectId | Long | ✅ | |
| campaignId | Long | ❌ | |
| userId | Long | ❌ | 특정 유저 반응 추적 |
| actionType | String | ❌ | OPEN/CLICK/CONVERT/DISMISS |
| startDate | String | ❌ | |
| endDate | String | ❌ | |
| page | Integer | ❌ | |
| size | Integer | ❌ | |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "actionId": 55123098,
        "logId": 98234521,
        "campaignId": 4521,
        "userId": 78901,
        "deviceId": 23456,
        "actionType": "OPEN",
        "actionAt": "2026-04-28T10:02:15.000Z"
      }
    ],
    "pagination": { "page": 1, "size": 20, "totalElements": 18234, "totalPages": 912 }
  }
}
```

---

# 📥 이벤트 수신 API

## 19. 사용자 반응 이벤트 수신

엔드유저 SDK가 오픈/클릭/전환 이벤트를 전송합니다. 이 API만 Public API Key 인증을 사용합니다.

| 항목 | 내용 |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/events/actions` |
| **권한** | Public API Key (`X-API-Key` 헤더) |

### Request Headers

```http
X-API-Key: pk_live_xxxxxxxxxxxxx
Content-Type: application/json
```

### Request Body

```json
{
  "logId": 98234521,
  "userId": 78901,
  "deviceId": 23456,
  "actionType": "CLICK",
  "actionTarget": "https://shop.example.com/promo",
  "userAgent": "Mozilla/5.0 (iPhone)...",
  "actionAt": "2026-04-28T10:02:32.000Z"
}
```

### Response 201 Created

```json
{
  "success": true,
  "data": {
    "actionId": 55123205,
    "acceptedAt": "2026-04-28T10:02:32.500Z"
  }
}
```

### Error Cases

- `400 BAD_REQUEST` - logId 존재하지 않음 또는 필수 파라미터 누락
- `401 UNAUTHORIZED` - API Key 무효
- `429 RATE_LIMITED` - 초당 요청 한도 초과

> 💡 `deviceId`는 NULL 허용 (이메일/웹 클릭은 device 없음)

---

# 🔍 감사 조회 API

## 20. 감사 로그 목록 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/audit-logs` |
| **권한** | OWNER 또는 ADMIN |

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| projectId | Long | ❌ | ADMIN은 생략 시 전체 조회 |
| memberId | Long | ❌ | 특정 회원의 작업만 |
| action | String | ❌ | FCM_KEY_UPDATE 등 |
| targetType | String | ❌ | PROJECT/MEMBER/API_KEY 등 |
| startDate | String | ❌ | |
| endDate | String | ❌ | |
| page | Integer | ❌ | |
| size | Integer | ❌ | |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "auditId": 10234,
        "projectId": 123,
        "memberId": 501,
        "memberName": "김철수",
        "action": "FCM_KEY_UPDATE",
        "targetType": "PROJECT",
        "targetId": 123,
        "ipAddress": "203.0.113.42",
        "userAgent": "Mozilla/5.0...",
        "createdAt": "2026-04-28T14:25:12.000Z"
      },
      {
        "auditId": 10233,
        "projectId": 123,
        "memberId": 502,
        "memberName": "이영희",
        "action": "MEMBER_ROLE_CHANGE",
        "targetType": "MEMBER",
        "targetId": 510,
        "ipAddress": "203.0.113.99",
        "userAgent": "Mozilla/5.0...",
        "createdAt": "2026-04-28T13:10:45.000Z"
      }
    ],
    "pagination": { "page": 1, "size": 20, "totalElements": 342, "totalPages": 18 }
  }
}
```

> 🔄 v2 변경: `ipAddress` 필드 복구

---

## 21. 감사 로그 상세 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/audit-logs/{auditId}` |
| **권한** | OWNER 또는 ADMIN |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "auditId": 10234,
    "projectId": 123,
    "memberId": 501,
    "memberName": "김철수",
    "memberEmail": "chulsoo@example.com",
    "action": "FCM_KEY_UPDATE",
    "targetType": "PROJECT",
    "targetId": 123,
    "detail": {
      "before": { "fcmKeyHash": "a1b2c3..." },
      "after": { "fcmKeyHash": "x9y8z7..." },
      "reason": "만료된 키 교체"
    },
    "ipAddress": "203.0.113.42",
    "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)...",
    "createdAt": "2026-04-28T14:25:12.000Z"
  }
}
```

---

# 🛡️ 정합성/운영 API

## 22. 캠페인 전체 정합성 검증 실행

| 항목 | 내용 |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/reconciliations/campaigns/{campaignId}` |
| **권한** | ADMIN |

### Description

캠페인의 모든 회차에 대해 정합성 검증을 실행합니다.

### Response 201 Created

```json
{
  "success": true,
  "data": {
    "campaignId": 4521,
    "totalExecutions": 12,
    "consistentCount": 11,
    "inconsistentCount": 1,
    "executions": [
      {
        "reconId": 2341,
        "campaignExecutionId": 88123,
        "runRound": 12,
        "channel": "PUSH",
        "expectedCount": 12500,
        "actualCount": 12498,
        "diffCount": 2,
        "isConsistent": false,
        "checkedAt": "2026-04-28T15:30:00.000Z"
      }
    ]
  }
}
```

---

## 23. 특정 회차 정합성 검증 실행 ✨ NEW

| 항목 | 내용 |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/reconciliations/executions/{executionId}` |
| **권한** | ADMIN |

### Response 201 Created

```json
{
  "success": true,
  "data": {
    "reconId": 2341,
    "campaignId": 4521,
    "campaignExecutionId": 88123,
    "runRound": 12,
    "channel": "PUSH",
    "expectedCount": 12500,
    "actualCount": 12498,
    "sentCount": 12180,
    "failedCount": 318,
    "diffCount": 2,
    "isConsistent": false,
    "note": "2건 누락 감지",
    "checkedAt": "2026-04-28T15:30:00.000Z"
  }
}
```

---

## 24. 정합성 검증 결과 목록 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/reconciliations` |
| **권한** | ADMIN |

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| projectId | Long | ❌ | |
| campaignId | Long | ❌ | |
| campaignExecutionId | Long | ❌ | ✨ NEW |
| isConsistent | Boolean | ❌ | false로 조회 시 문제 건만 |
| startDate | String | ❌ | |
| endDate | String | ❌ | |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "reconId": 2341,
        "campaignId": 4521,
        "campaignExecutionId": 88123,
        "runRound": 12,
        "channel": "PUSH",
        "expectedCount": 12500,
        "actualCount": 12498,
        "diffCount": 2,
        "isConsistent": false,
        "checkedAt": "2026-04-28T15:30:00.000Z"
      }
    ],
    "pagination": { "page": 1, "size": 20, "totalElements": 48 }
  }
}
```

---

## 25. 불일치 정합성 검증 건만 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/reconciliations/inconsistent` |
| **권한** | ADMIN |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "totalInconsistent": 3,
    "items": [
      {
        "reconId": 2341,
        "campaignId": 4521,
        "campaignExecutionId": 88123,
        "campaignName": "4월 신상품 알림",
        "runRound": 12,
        "channel": "PUSH",
        "diffCount": 2,
        "note": "2건 누락 감지",
        "checkedAt": "2026-04-28T15:30:00.000Z"
      }
    ]
  }
}
```

---

## 26. 시스템 헬스 스냅샷 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/system/health` |
| **권한** | ADMIN |

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| metricName | String | ❌ | | 특정 메트릭만 |
| status | String | ❌ | | OK/WARN/CRITICAL |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "snapshotTime": "2026-04-28T15:30:00.000Z",
    "items": [
      {
        "snapshotId": 88123,
        "metricName": "kafka_lag",
        "metricTarget": "notification-send-result",
        "metricValue": 1250.0000,
        "thresholdWarn": 10000.0000,
        "thresholdCrit": 50000.0000,
        "status": "OK",
        "collectedAt": "2026-04-28T15:29:45.000Z"
      },
      {
        "snapshotId": 88122,
        "metricName": "api_error_rate",
        "metricTarget": "/api/v1/events/actions",
        "metricValue": 2.35,
        "thresholdWarn": 5.0,
        "thresholdCrit": 10.0,
        "status": "OK",
        "collectedAt": "2026-04-28T15:29:45.000Z"
      }
    ]
  }
}
```

---

## 27. 특정 메트릭 시계열 조회

| 항목 | 내용 |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/system/health/metrics` |
| **권한** | ADMIN |

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| metricName | String | ✅ | kafka_lag 등 |
| metricTarget | String | ❌ | 특정 대상 필터 |
| startTime | String | ✅ | ISO-8601 |
| endTime | String | ✅ | ISO-8601 |

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "metricName": "kafka_lag",
    "metricTarget": "notification-send-result",
    "period": {
      "startTime": "2026-04-28T14:00:00.000Z",
      "endTime": "2026-04-28T15:30:00.000Z"
    },
    "series": [
      { "collectedAt": "2026-04-28T15:29:45.000Z", "value": 1250.0, "status": "OK" },
      { "collectedAt": "2026-04-28T15:24:45.000Z", "value": 1180.0, "status": "OK" },
      { "collectedAt": "2026-04-28T15:19:45.000Z", "value": 1320.0, "status": "OK" }
    ]
  }
}
```

---

# 🔄 내부 API

> **인증**: 내부 네트워크 전용 + `X-Internal-Token` 헤더  
> 외부 노출 금지 (API Gateway에서 차단)

## 28. 시간 집계 배치 트리거

스케줄러가 5분마다 호출.

| 항목 | 내용 |
|---|---|
| **Method** | `POST` |
| **Path** | `/internal/stats/aggregate/hourly` |
| **권한** | Internal Token |

### Request Body

```json
{
  "bucketHour": "2026-04-28T15:00:00",
  "projectIds": [123, 124, 125],
  "force": false
}
```

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "processedProjects": 3,
    "processedRows": 487,
    "elapsedMs": 2341,
    "bucketHour": "2026-04-28T15:00:00"
  }
}
```

---

## 29. 일 집계 롤업 배치 트리거

매일 새벽 1시 스케줄러가 호출.

| 항목 | 내용 |
|---|---|
| **Method** | `POST` |
| **Path** | `/internal/stats/rollup/daily` |
| **권한** | Internal Token |

### Request Body

```json
{
  "bucketDate": "2026-04-27",
  "targets": ["campaign", "template", "failure", "project"],
  "force": false
}
```

### Response 200 OK

```json
{
  "success": true,
  "data": {
    "bucketDate": "2026-04-27",
    "results": {
      "campaign": { "processedRows": 1240, "elapsedMs": 4521 },
      "template": { "processedRows": 89, "elapsedMs": 892 },
      "failure": { "processedRows": 34, "elapsedMs": 312 },
      "project": { "processedRows": 15, "elapsedMs": 156 }
    },
    "totalElapsedMs": 5881
  }
}
```

---

## 30. Kafka 컨슈머 → 로그 적재 (내부)

Kafka 컨슈머 워커가 HTTP 대신 DB 직접 접근하는 경우가 많지만, HTTP API로도 제공.

| 항목 | 내용 |
|---|---|
| **Method** | `POST` |
| **Path** | `/internal/notification-logs` |
| **권한** | Internal Token |

### Request Body

```json
{
  "messageKey": "kafka-msg-abc123",
  "projectId": 123,
  "campaignId": 4521,
  "campaignExecutionId": 88123,
  "messageTemplateId": 89,
  "userId": 78901,
  "deviceId": 23456,
  "channel": "PUSH",
  "status": "SENT",
  "sentAt": "2026-04-28T10:00:05.123Z"
}
```

### Response 201 Created

```json
{
  "success": true,
  "data": {
    "logId": 98234521,
    "createdAt": "2026-04-28T10:00:05.500Z"
  }
}
```

### Note

- `messageKey` 기반 멱등성 보장. 중복 요청 시 기존 logId 반환 + 200 OK.
- `campaignExecutionId`는 발송 워커에서 Kafka 메시지로 전달받음

> 🔄 v2 변경: `campaignExecutionId`, `messageTemplateId` 필드 추가

---

# 📌 에러 코드 전체 목록

| HTTP | Code | 발생 상황 |
|---|---|---|
| 400 | `INVALID_DATE_RANGE` | 날짜 범위 잘못됨 (최대 90일 초과 등) |
| 400 | `INVALID_ENUM_VALUE` | channel, status 등 유효하지 않은 값 |
| 400 | `LOG_NOT_FOUND` | 이벤트 수신 시 logId 존재하지 않음 |
| 400 | `EXECUTION_NOT_FOUND` | 회차 ID 존재하지 않음 ✨ NEW |
| 401 | `TOKEN_EXPIRED` | Access Token 만료 |
| 401 | `TOKEN_INVALID` | 토큰 서명 오류 |
| 401 | `INVALID_API_KEY` | Public API Key 무효 |
| 403 | `PROJECT_ACCESS_DENIED` | 해당 프로젝트 멤버 아님 |
| 403 | `ADMIN_REQUIRED` | ADMIN 권한 필요 |
| 403 | `OWNER_REQUIRED` | OWNER 권한 필요 |
| 404 | `CAMPAIGN_NOT_FOUND` | 캠페인 없음 |
| 404 | `TEMPLATE_NOT_FOUND` | 템플릿 없음 |
| 404 | `AUDIT_NOT_FOUND` | 감사 로그 없음 |
| 429 | `RATE_LIMITED` | 요청 한도 초과 |
| 500 | `AGGREGATION_FAILED` | 집계 배치 실패 |
| 500 | `RECONCILIATION_FAILED` | 정합성 검증 실패 |

---

# 📎 부록: API 변경 매트릭스 (v1 → v2)

| API | 변경 사항 |
|---|---|
| 발송 로그 응답 전체 | `messageTemplateId`, `campaignExecutionId` 필드 추가 |
| 발송 로그 status | ENUM에서 `CLICKED` 제거 |
| 발송 로그 목록 쿼리 | `messageTemplateId`, `campaignExecutionId` 필터 추가 |
| 감사 로그 응답 | `ipAddress` 필드 복구 |
| 템플릿 관련 API | `templateId` → `messageTemplateId` 명명 통일 |
| 캠페인 회차 API | 4개 신설 (목록/상세/통계/로그) |
| 회차 정합성 검증 API | 신설 |
| 최근 캠페인 응답 | `status` → `campaignStatus` + `lastExecutionStatus` 분리 |
| 정합성 검증 응답 | `campaignExecutionId`, `runRound` 필드 추가 |
| 내부 로그 적재 API | `campaignExecutionId`, `messageTemplateId` 필드 추가 |

---

## 💡 Notion 붙여넣기 팁

- 이 내용을 Notion에 붙여넣으면 Markdown 자동 변환됩니다
- 각 API 섹션을 **Toggle 블록**으로 감싸면 목록 보기 좋아져요
- 에러 코드 테이블은 Notion **Database** 형태로 전환하면 필터링 가능

---

_Last updated: 2026-04-28_  
_Version: v2.0_  
_Owner: 이재영_
