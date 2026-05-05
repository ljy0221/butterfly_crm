# Sprint 7 — Monitoring Domain API Specification

> **Backend Liaison Agent** Monitoring (Event, Notification, Audit) Specification  
> **Created**: 2026-05-02  
> **Backend**: Spring Boot 3.5.13 (butterfly-event, butterfly-notification, butterfly-reporting)  
> **Status**: Event - POST collect DONE, Categories/Schema/List/Search NOT IMPLEMENTED | Notification - 8 Endpoints COMPLETE | Audit - 2 Endpoints COMPLETE

---

## 1. Overview

Sprint 7 integrates **three monitoring domains** into a unified dashboard:

| Domain | Purpose | Implementation Status |
|--------|---------|----------------------|
| **Event** | Event collection & schema management | POST collect API ✓, Categories/Schema/List/Search ❌ |
| **Notification** | Delivery log tracking & monitoring | 8 endpoints (Credentials, FCM, Logs, Callbacks) ✓ |
| **Audit** | Compliance & access tracking | List & Detail queries ✓ |

---

## 2. Common Specification

### 2.1 API Response Format

All responses conform to `ApiResponse<T>`:

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

Failure:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message"
  }
}
```

### 2.2 Pagination

Responses supporting pagination include `PageResponse`:

```json
{
  "content": [],
  "currentPage": 0,
  "totalPages": 5,
  "totalElements": 100,
  "pageSize": 20
}
```

### 2.3 Authentication & Headers

| Header | Type | Required | Purpose |
|--------|------|----------|---------|
| `X-Member-Id` | Long | YES (most endpoints) | Current member ID |
| `X-Project-Id` | Long | YES (event collect) | Project context (Public API Key auth) |
| `X-API-Key` | String | YES (SDK callbacks) | SDK device authentication |

### 2.4 Permission Matrix

| Endpoint | VIEWER | EDITOR | OWNER | ADMIN |
|----------|:------:|:------:|:-----:|:-----:|
| Event list/search/detail | YES | YES | YES | YES |
| Event categories (mock) | YES | YES | YES | YES |
| Event schema (mock) | YES | YES | YES | YES |
| Notification logs | YES | YES | YES | YES |
| Audit logs | NO | NO | YES | YES |
| Audit detail | NO | NO | YES | YES |

---

## 3. Event Domain

### 3.1 POST /api/v1/events — Collect Single Event

**Auth**: X-Project-Id (Public API Key)  
**Request**:
```http
POST /api/v1/events
X-Project-Id: 1
Content-Type: application/json

{
  "userId": 12345,
  "deviceId": 67890,
  "eventCategory": "product_view",
  "occurredAt": "2026-05-02T14:30:00",
  "eventData": {
    "productId": 999,
    "price": 29.99,
    "currency": "USD"
  }
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "eventId": "evt_abc123xyz",
    "acceptedAt": "2026-05-02T14:30:05Z",
    "schemaVersion": 1
  },
  "error": null
}
```

**DTO**:
```
EventCollectRequest:
  - userId: Long (required)
  - deviceId: Long (required)
  - eventCategory: String (required, non-blank)
  - occurredAt: LocalDateTime (required)
  - eventData: JsonNode (optional)

EventCollectResponse:
  - eventId: String
  - acceptedAt: LocalDateTime
  - schemaVersion: Integer
```

**Errors**:
| Code | HTTP | Description |
|------|:----:|-------------|
| CATEGORY_NOT_FOUND | 404 | Event category not registered |
| INTERNAL_ERROR | 500 | Server processing error |

---

### 3.2 GET /api/v1/events/categories — List Event Categories ❌ (MOCK)

**Implementation**: Not yet in backend; mock only.

**Request**:
```http
GET /api/v1/events/categories
X-Member-Id: 1
```

**Response** (200 OK):
```json
{
  "success": true,
  "data": [
    {
      "categoryId": 1,
      "categoryName": "product_view",
      "displayName": "상품 조회",
      "description": "사용자가 상품을 조회했음",
      "eventCount": 150000,
      "lastCollectedAt": "2026-05-02T14:30:00Z"
    }
  ],
  "error": null
}
```

---

## 4. Notification Domain

### 4.1 PUT /api/projects/{projectId}/credentials/{channel} — Save Credential

**Auth**: X-Member-Id  
**Channel**: PUSH | EMAIL | SMS | WEBHOOK

**Request**:
```http
PUT /api/projects/1/credentials/PUSH
X-Member-Id: 2
Content-Type: application/json

{
  "credentialData": "{\"type\": \"service_account\", ...}"
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

### 4.2 GET /api/projects/{projectId}/credentials/{channel} — Retrieve Credential

### 4.3 DELETE /api/projects/{projectId}/credentials/{channel} — Delete Credential

### 4.4 POST /api/projects/{projectId}/fcm/send — Send FCM Message

**Request**:
```json
{
  "credentialJson": "{...FCM service account...}",
  "tokens": ["token1", "token2"],
  "title": "Test Notification",
  "body": "This is a test message",
  "imageUrl": "https://example.com/image.jpg"
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "requestId": "req_abc123",
    "successCount": 2,
    "failureCount": 0
  },
  "error": null
}
```

### 4.5 DELETE /api/projects/{projectId}/fcm/credentials/cache — Invalidate Cache

### 4.6 GET /api/projects/{projectId}/campaigns/{campaignId}/logs — Campaign Logs

**Query Params**: channel, status, page, size

**Response**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "logId": 1,
        "campaignId": 5,
        "userId": 100,
        "deviceId": 200,
        "channel": "PUSH",
        "status": "DELIVERED",
        "sentAt": "2026-05-02T10:00:00Z",
        "deliveredAt": "2026-05-02T10:00:05Z",
        "clickedAt": null
      }
    ],
    "currentPage": 0,
    "totalPages": 10,
    "totalElements": 200,
    "pageSize": 20
  },
  "error": null
}
```

### 4.7 GET /api/projects/{projectId}/logs/{logId} — Log Detail

**Response**:
```json
{
  "success": true,
  "data": {
    "logId": 1,
    "projectId": 1,
    "campaignId": 5,
    "userId": 100,
    "deviceId": 200,
    "channel": "PUSH",
    "recipient": "fcm_token_abc123...",
    "status": "DELIVERED",
    "errorCode": null,
    "errorMessage": null,
    "sentAt": "2026-05-02T10:00:00Z",
    "deliveredAt": "2026-05-02T10:00:05Z",
    "clickedAt": null,
    "createdAt": "2026-05-02T10:00:00Z"
  },
  "error": null
}
```

### 4.8 GET /api/projects/{projectId}/users/{userId}/logs — User Logs

### 4.9 POST /sdk/v1/notifications/{logId}/delivered — Delivery Callback

### 4.10 POST /sdk/v1/notifications/{logId}/clicked — Click Callback

---

## 5. Audit Domain

### 5.1 GET /api/v1/audit-logs — List Audit Logs

**Auth**: X-Member-Id  
**Permission**: OWNER or ADMIN  

**Query Params**: projectId, memberId, action, targetType, startDate, endDate, page, size

**Response**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "auditId": 1001,
        "projectId": 1,
        "memberId": 2,
        "memberName": null,
        "action": "CREATE",
        "targetType": "CAMPAIGN",
        "targetId": 5,
        "ipAddress": "203.0.113.45",
        "userAgent": "Mozilla/5.0...",
        "createdAt": "2026-05-02T14:30:00Z"
      }
    ],
    "currentPage": 0,
    "totalPages": 3,
    "totalElements": 50,
    "pageSize": 20
  },
  "error": null
}
```

### 5.2 GET /api/v1/audit-logs/{auditId} — Audit Log Detail

**Response**:
```json
{
  "success": true,
  "data": {
    "auditId": 1001,
    "projectId": 1,
    "memberId": 2,
    "memberName": null,
    "memberEmail": null,
    "action": "CREATE",
    "targetType": "CAMPAIGN",
    "targetId": 5,
    "detail": {
      "campaignName": "Summer Sale 2026",
      "campaignType": "ONE_TIME",
      "channel": "EMAIL",
      "status": "DRAFT"
    },
    "ipAddress": "203.0.113.45",
    "userAgent": "Mozilla/5.0...",
    "createdAt": "2026-05-02T14:30:00Z"
  },
  "error": null
}
```

---

## 6. Error Codes

### Event Domain
```
EventErrorCode {
  CATEGORY_NOT_FOUND: (404, "등록되지 않은 이벤트 카테고리입니다.")
  INTERNAL_ERROR: (500, "서버 내부 오류가 발생했습니다.")
}
```

### Notification Domain
```
DeliveryErrorCode {
  LOG_NOT_FOUND: (404)
  FCM_SEND_FAILED: (500)
  FCM_CREDENTIALS_NOT_FOUND: (400)
  ... (19 total error codes)
}
```

### Audit Domain
```
AnalyticsErrorCode {
  AUDIT_NOT_FOUND: (404)
  INVALID_DATE_RANGE: (400)
}
```

---

## 7. Mock-Fallback Configuration

**Frontend Config** (`src/config/mock.ts`):
```typescript
export const MOCK_FALLBACK_CONFIG = {
  event_collect: false,        // Backend implemented ✓
  event_categories: true,      // Mock fallback ❌
  event_schema: true,          // Mock fallback ❌
  event_list: true,            // Mock fallback ❌
  event_search: true,          // Mock fallback ❌

  notification_credentials: false,  // Backend ✓
  notification_fcm_send: false,     // Backend ✓
  notification_logs_campaign: false,// Backend ✓
  notification_logs_user: false,    // Backend ✓
  notification_logs_detail: false,  // Backend ✓
  notification_callbacks: false,    // Backend ✓

  audit_logs_list: false,      // Backend ✓
  audit_logs_detail: false,    // Backend ✓
};
```

---

## 8. Backend Source References

**Event**:
- Controller: `/butterfly-event/src/main/java/.../controller/EventCollectController.java`
- DTO: EventCollectRequest, EventCollectResponse
- Error: EventErrorCode

**Notification**:
- Controllers: ChannelCredentialController, FcmController, NotificationLogController, NotificationCallbackController
- DTO: NotificationLogResponse, NotificationLogDetailResponse, FcmSendRequest
- Error: DeliveryErrorCode

**Audit**:
- Controller: `/butterfly-reporting/src/main/java/.../audit/controller/AuditLogController.java`
- DTO: AuditLogListItem, AuditLogDetailResponse
- Entity: AuditLog (append-only, @Immutable)
- Error: AnalyticsErrorCode

---

**Document Version**: 1.0  
**Last Updated**: 2026-05-02  
**Status**: Ready for Sprint 7 Frontend Implementation
