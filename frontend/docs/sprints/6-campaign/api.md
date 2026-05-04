# Sprint 6 — Campaign Domain API Specification

> **Backend Liaison Agent** Campaign Domain Specification  
> **Created**: 2026-05-01  
> **Backend**: Spring Boot 3.5.13 (butterfly-campaign)  
> **Status**: Campaign CRUD/Execution API - NOT IMPLEMENTED | Domain Model + Business Logic - COMPLETE

---

## 1. Domain Overview

### 1.1 Campaign Domain Scope

Campaign domain manages marketing campaign creation and execution.  
Sprint 6 targets:

- Campaign CRUD (Create, Read List/Detail, Update, Delete via soft delete)
- Campaign execution control (Start → ACTIVE, Pause)
- Send channels: FCM(PUSH), EMAIL, SMS, WEBHOOK, KAKAO
- Campaign types: ONE_TIME, AUTOMATION
- Send modes: IMMEDIATE, SCHEDULED, RECURRING
- Segment targeting or send-to-all
- Message template reference

### 1.2 Endpoints (Sprint 6 Plan)

| # | Endpoint | Method | Status | Priority |
|---|----------|--------|--------|----------|
| 1 | /api/v1/projects/{projectId}/campaigns | GET | NOT IMPLEMENTED | P0 |
| 2 | /api/v1/projects/{projectId}/campaigns/{campaignId} | GET | NOT IMPLEMENTED | P0 |
| 3 | /api/v1/projects/{projectId}/campaigns | POST | NOT IMPLEMENTED | P0 |
| 4 | /api/v1/projects/{projectId}/campaigns/{campaignId} | PUT | NOT IMPLEMENTED | P0 |
| 5 | /api/v1/projects/{projectId}/campaigns/{campaignId} | DELETE | NOT IMPLEMENTED | P0 |
| 6 | /api/v1/projects/{projectId}/campaigns/{campaignId}/start | POST | NOT IMPLEMENTED | P0 |
| 7 | /api/v1/projects/{projectId}/campaigns/{campaignId}/pause | POST | NOT IMPLEMENTED | P1 |

### 1.3 Permission Matrix

| Endpoint | VIEWER | EDITOR | OWNER | ADMIN |
|----------|:------:|:------:|:-----:|:-----:|
| GET list/detail | YES | YES | YES | YES |
| POST create | NO | YES | YES | YES |
| PUT update | NO | YES | YES | YES |
| DELETE | NO | YES | YES | YES |
| POST start | NO | YES | YES | YES |
| POST pause | NO | YES | YES | YES |

---

## 2. Backend Implementation Status

### 2.1 Domain Model (COMPLETE)

**Campaign Entity**:
- campaignId, projectId, memberId, messageTemplateId, segmentId
- campaignName, campaignDescription
- campaignType (ONE_TIME | AUTOMATION)
- channel (PUSH, EMAIL, SMS, WEBHOOK, KAKAO)
- sendMode (IMMEDIATE, SCHEDULED, RECURRING)
- campaignStatus (DRAFT, ACTIVE, PAUSED, ENDED)
- Schedule fields: nextRunAt, recurringCron, repeatType, repeatDayOfWeek, repeatDayOfMonth, repeatTime, recurringCount, recurringExecutionCount
- Audit: createdAt, updatedAt (SoftDeletedDomain)
- Soft delete: isDeleted, deletedAt
- Optimistic lock: version

**CampaignExecution Entity**:
- campaignExecutionId, campaignId, runRound
- campaignExecutionStatus (READY, SEND, SUCCESS, FAIL)
- Metrics: sendStartedAt, sendCompletedAt, targetCount, sentCount, failedCount, failReason

**Factory Methods** (Campaign.java):
- immediateAll(), immediateTarget()
- scheduledAll(), scheduledTarget()
- recurringAll(), recurringTarget()
- automationAll(), automationTarget()

**Cron Generator** (RecurringCronGenerator.java):
- DAILY: 0 {minute} {hour} * * *
- WEEKLY: 0 {minute} {hour} * * {MON,TUE,...}
- MONTHLY: 0 {minute} {hour} {dayOfMonth} * *

### 2.2 Application/Presentation Layer (NOT IMPLEMENTED)

Missing:
- CampaignService, CampaignRepository, CampaignController
- DTOs (CampaignCreateRequest, CampaignDetailResponse, etc.)

Conclusion: Backend has domain model only; NO REST APIs exist.

---

## 3. Mock-Fallback API Specification

### 3.1 GET /api/v1/projects/{projectId}/campaigns (List)

Request:
```
GET /api/v1/projects/1/campaigns?status=ACTIVE&channel=EMAIL&page=0&size=20
X-Member-Id: 1
```

Response:
```json
{
  "success": true,
  "data": {
    "campaigns": [
      {
        "campaignId": 1,
        "projectId": 1,
        "campaignName": "Summer Sale",
        "campaignType": "ONE_TIME",
        "channel": "EMAIL",
        "campaignStatus": "ACTIVE",
        "sendCount": 15000,
        "openRate": 32.5,
        "createdAt": "2026-04-28T10:30:00Z",
        "createdBy": "user@example.com",
        "messageTemplateName": "Summer Promo"
      }
    ],
    "pagination": {
      "currentPage": 0,
      "totalPages": 2,
      "totalElements": 25,
      "pageSize": 20
    }
  },
  "error": null
}
```

### 3.2 GET /api/v1/projects/{projectId}/campaigns/{campaignId} (Detail)

Response includes schedule polymorphism:

```json
{
  "schedule": {
    "sendMode": "IMMEDIATE",
    "scheduledAt": null,
    "repeat": null
  }
}
```

Variants: IMMEDIATE, SCHEDULED, RECURRING (with repeat object)

### 3.3 POST /api/v1/projects/{projectId}/campaigns (Create)

Request:
```json
{
  "campaignName": "Summer Sale",
  "campaignType": "ONE_TIME",
  "campaignDescription": "2026 promotion",
  "channel": "EMAIL",
  "segmentId": 5,
  "messageTemplateId": 7,
  "sendMode": "IMMEDIATE",
  "schedule": { "type": "immediate" },
  "isTempSave": false
}
```

### 3.4 PUT /api/v1/projects/{projectId}/campaigns/{campaignId} (Update)

Constraint: DRAFT status only

### 3.5 DELETE /api/v1/projects/{projectId}/campaigns/{campaignId}

Uses soft delete (is_deleted = true)

### 3.6 POST /api/v1/projects/{projectId}/campaigns/{campaignId}/start

Behavior:
1. DRAFT → ACTIVE transition
2. Create CampaignExecution (READY status)
3. Enqueue send job
4. Return 200 OK (async)

### 3.7 POST /api/v1/projects/{projectId}/campaigns/{campaignId}/pause

Constraint: ACTIVE status only

---

## 4. Campaign Status Flow

| Status | Edit | Delete | Start | Pause |
|--------|:----:|:------:|:-----:|:-----:|
| DRAFT | YES | YES | YES | - |
| ACTIVE | NO | NO | - | YES |
| PAUSED | NO | NO | - | - |
| ENDED | NO | NO | - | - |

---

## 5. Schedule Polymorphism

### Immediate
```json
{ "sendMode": "IMMEDIATE", "type": "immediate" }
```

### Scheduled
```json
{
  "sendMode": "SCHEDULED",
  "type": "scheduled",
  "scheduledAt": "2026-05-10T14:30:00Z"
}
```

### Recurring
```json
{
  "sendMode": "RECURRING",
  "type": "recurring",
  "repeatType": "DAILY|WEEKLY|MONTHLY",
  "repeatDayOfWeek": "MON,WED,FRI",
  "repeatDayOfMonth": 15,
  "repeatTime": "14:30:00",
  "recurringCount": 10,
  "nextRunAt": "2026-05-03T14:30:00Z"
}
```

---

## 6. Mock Implementation (Frontend)

Store structure:
- campaigns: Map<campaignId, Campaign>
- executions: Map<executionId, CampaignExecution>
- nextCampaignId, nextExecutionId counters

MSW handlers for all 7 endpoints with validation:
- Campaign name/description required
- Message template exists check
- Segment exists check (if provided)
- Status constraints enforcement
- Cron generation for recurring campaigns

---

## 7. Error Codes

| Code | HTTP | Description |
|------|:----:|-------------|
| CAMPAIGN_001 | 400 | Project required |
| CAMPAIGN_002 | 400 | Message template required |
| CAMPAIGN_003 | 400 | Member required |
| CAMPAIGN_004 | 400 | Campaign name required |
| CAMPAIGN_005 | 400 | Description required |
| CAMPAIGN_006 | 400 | Segment required |
| CAMPAIGN_007 | 400 | Scheduled time required |
| CAMPAIGN_008 | 400 | Scheduled time in past |
| CAMPAIGN_009 | 400 | Repeat type required |
| CAMPAIGN_010 | 400 | Recurring count >= 1 |
| CAMPAIGN_011 | 400 | Repeat time required |
| CAMPAIGN_012 | 400 | Day of week required |
| CAMPAIGN_013 | 400 | Invalid day of week |
| CAMPAIGN_014 | 400 | Day of month 1-31 |
| CAMPAIGN_NOT_FOUND | 404 | Campaign not found |
| CAMPAIGN_ALREADY_ACTIVE | 409 | Already started |
| OPTIMISTIC_LOCK_FAILURE | 409 | Version conflict |

---

## 8. Frontend Checklist

### Mock Implementation
- src/api/_mock/campaigns.ts (store + handlers)
- MSW endpoint registration
- Initial test data
- Validation logic

### UI Components
- Campaign list page (filters, pagination)
- Campaign detail view
- 6-step creation wizard
- Update form
- Schedule selector (Immediate/Scheduled/Recurring)
- Action buttons (start/pause/delete)

### Testing
- API mock call tests
- Form validation tests
- State transition tests
- Cron generation tests

---

## References

**Backend Source**:
- Domain: /butterfly-campaign/src/main/java/.../campaign/domain/
- Error Codes: /butterfly-campaign/src/.../campaign/exception/code/CampaignErrorCode.java
- Cron Generator: /butterfly-campaign/src/.../campaign/domain/RecurringCronGenerator.java

**Next Sprint**:
- Sprint 7: CampaignService, CampaignRepository, CampaignController
- Sprint 8: Pause + permission enforcement
- Sprint 9: Real-time dashboard (WebSocket)

---

**Status**: COMPLETE (Specification)  
**Backend**: Domain ready, API NOT IMPLEMENTED  
**Frontend**: Ready for mock implementation