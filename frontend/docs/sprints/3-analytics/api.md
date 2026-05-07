# Sprint 3 — Analytics (Dashboard/Metrics) API 명세

> Backend Liaison Agent가 정리한 백엔드 → 프론트엔드 계약서.  
> **작성 일시**: 2026-05-02  
> **백엔드 버전**: Spring Boot 3.5.13 (butterfly-reporting)  
> **상태**: 권한 검증 완료 (클래스 레벨 @PreAuthorize 적용)
> **2026-05-07 갱신**: ADR-0007 supersede 적용. 본 문서 §부록 "ADR-0007 적용" 참조.
> 자세한 변경 이력은 [`docs/changes/2026-05-07-adr0007-application.md`](../../../../docs/changes/2026-05-07-adr0007-application.md).

---

## 1. 도메인 개요 및 권한 매트릭스

### 1.1 Analytics 도메인 범위

Analytics는 **대시보드 통계, 캠페인 성과, 채널·세그먼트별 메트릭**을 제공하는 조회 전용 도메인입니다.
Sprint 3에서는 **대시보드 메인 페이지** 우선 구현을 목표로 하며, 캠페인 상세/로그는 Sprint 6~7에서 추가됩니다.

**포함 엔드포인트**:
- API 57: GET `/api/projects/{projectId}/analytics/dashboard` — 대시보드 요약 + 일일 추이
- API 58: GET `/api/projects/{projectId}/campaigns/{campaignId}/stats` — 캠페인 누적 통계
- API NEW: GET `/api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats` — 회차별 상세
- API 59: GET `/api/projects/{projectId}/analytics/campaigns` — 캠페인 성과 페이지네이션
- API 60: GET `/api/projects/{projectId}/analytics/channels` — 채널별 메트릭
- API 61: GET `/api/projects/{projectId}/analytics/segments` — 세그먼트별 메트릭

### 1.2 권한 매트릭스

| 엔드포인트 | 공개 | VIEWER | EDITOR | OWNER | ADMIN |
|---|:---:|:---:|:---:|:---:|:---:|
| GET /api/projects/{projectId}/analytics/dashboard | ❌ | ✅ | ✅ | ✅ | ✅ |
| GET /api/projects/{projectId}/analytics/campaigns | ❌ | ✅ | ✅ | ✅ | ✅ |
| GET /api/projects/{projectId}/analytics/channels | ❌ | ✅ | ✅ | ✅ | ✅ |
| GET /api/projects/{projectId}/analytics/segments | ❌ | ✅ | ✅ | ✅ | ✅ |
| GET /api/projects/{projectId}/campaigns/{campaignId}/stats | ❌ | ✅ | ✅ | ✅ | ✅ |
| GET /api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats | ❌ | ✅ | ✅ | ✅ | ✅ |

**권한 정책**: 클래스 레벨 `@PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")` 적용 → **VIEWER 이상 모두 조회 가능**.

---

## 2. 공통 사항

### 2.1 응답 Envelope

모든 응답은 `ApiResponse<T>` 래퍼:

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

### 2.2 인증 & 인가

**헤더**:
```
X-Member-Id: {memberId}
```

모든 엔드포인트는 `VIEWER` 이상 권한 필수.

### 2.3 날짜 파라미터 형식

- **Format**: `yyyy-MM-dd` (LocalDate)
- **타임존**: Asia/Seoul 기준

### 2.4 프로젝트 ID 위치

모든 Analytics 엔드포인트는 **path variable**에 projectId를 명시:
```
/api/projects/{projectId}/analytics/*
/api/projects/{projectId}/campaigns/{campaignId}/*
```

---

## 3. API 57 — GET /api/projects/{projectId}/analytics/dashboard

### 목적
대시보드 메인 페이지에 표시할 요약 통계 + 일일 추이 차트 데이터.

### 인증 & 권한
- **필수**: X-Member-Id  
- **권한**: VIEWER 이상  
- **HTTP**: 200 OK

### 요청

**URL**:
```
GET /api/projects/{projectId}/analytics/dashboard?from={from}&to={to}
```

**쿼리 파라미터**:
| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| from | LocalDate | ✅ | 조회 시작일 (yyyy-MM-dd) |
| to | LocalDate | ✅ | 조회 종료일 (yyyy-MM-dd) |

### 응답 (HTTP 200 OK)

```json
{
  "success": true,
  "data": {
    "period": {
      "from": "2026-04-01",
      "to": "2026-05-01"
    },
    "summary": {
      "campaignsSentInPeriod": 5,
      "totalSent": 25000,
      "totalDelivered": 24500,
      "totalClicked": 4900,
      "totalFailed": 500,
      "avgDeliveryRate": 0.98,
      "avgClickRate": 0.2
    },
    "dailyTrend": [
      {
        "date": "2026-04-01",
        "sent": 1000,
        "delivered": 980,
        "clicked": 196
      }
    ],
    "metricsComputedAt": "2026-05-02T14:30:00"
  },
  "error": null
}
```

### 응답 필드

**summary**:
- `campaignsSentInPeriod` (long): 기간 내 발송한 캠페인 수
- `totalSent` (long): 총 발송 건수
- `totalDelivered` (long): 총 전달 건수
- `totalClicked` (long): 총 클릭 건수
- `totalFailed` (long): 총 실패 건수
- `avgDeliveryRate` (double): 평균 전달률 (0.0~1.0)
- `avgClickRate` (double): 평균 클릭률 (0.0~1.0)

**dailyTrend**:
- `date`, `sent`, `delivered`, `clicked` (각 일자 통계)

**metricsComputedAt** (LocalDateTime): 집계 기준 시각

### 프론트엔드 UI 매핑

| 응답 필드 | UI 컴포넌트 |
|---|---|
| `summary.campaignsSentInPeriod` | KPI 카드: "기간 내 캠페인" |
| `summary.totalDelivered / totalSent * 100` | KPI 카드: "전달률" (%) |
| `summary.totalClicked / totalDelivered * 100` | KPI 카드: "오픈률" (%) |
| `dailyTrend` | 라인 차트: sent(파랑), delivered(초록), clicked(주황) |


---

## 4. API 59 — GET /api/projects/{projectId}/analytics/campaigns

### 목적
일정 기간 내 캠페인별 성과 비교 (페이지네이션).

### 요청

**URL**:
```
GET /api/projects/{projectId}/analytics/campaigns?from={from}&to={to}&page={page}&size={size}&channel={channel}&segmentId={segmentId}
```

**쿼리 파라미터**:
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| from | LocalDate | ✅ | - | 조회 시작일 |
| to | LocalDate | ✅ | - | 조회 종료일 |
| page | int | ❌ | 0 | 페이지 번호 (0-indexed) |
| size | int | ❌ | 10 | 페이지당 항목 수 |
| channel | String | ❌ | - | 채널 필터 (PUSH / EMAIL) |
| segmentId | Long | ❌ | - | 세그먼트 ID 필터 |

### 응답 (HTTP 200 OK)

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "campaignId": 1,
        "campaignName": "Spring 신상품 홍보",
        "channel": "PUSH",
        "sentCount": 5000,
        "deliveredCount": 4900,
        "clickedCount": 980,
        "failedCount": 100,
        "deliveryRate": 0.98,
        "clickRate": 0.2
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1
  },
  "error": null
}
```

### 응답 필드

**content** (CampaignMetricItem[]):
- `campaignId`, `campaignName`: 캠페인 식별 정보
- `channel` (String): PUSH, EMAIL 등
- `sentCount`, `deliveredCount`, `clickedCount`, `failedCount`: 발송 통계
- `deliveryRate`, `clickRate` (double): 비율 (0.0~1.0)

**페이지네이션**: `page`, `size`, `totalElements`, `totalPages`

---

## 5. API 60 — GET /api/projects/{projectId}/analytics/channels

### 목적
일정 기간 내 채널별 성과 메트릭 조회.

### 요청

**URL**:
```
GET /api/projects/{projectId}/analytics/channels?from={from}&to={to}
```

### 응답 (HTTP 200 OK)

```json
{
  "success": true,
  "data": {
    "channels": [
      {
        "channel": "PUSH",
        "sentCount": 10000,
        "deliveredCount": 9800,
        "clickedCount": 1960,
        "failedCount": 200,
        "deliveryRate": 0.98,
        "clickRate": 0.2
      }
    ]
  },
  "error": null
}
```

### 응답 필드

**channels** (ChannelMetricItem[]):
- `channel` (String): 채널명 (PUSH, EMAIL 등)
- `sentCount`, `deliveredCount`, `clickedCount`, `failedCount`: 발송 통계
- `deliveryRate`, `clickRate` (double): 비율

### 프론트엔드 UI 매핑

| 용도 | 응답 필드 |
|---|---|
| 채널 비교 카드 | `channels` 리스트 |
| 도넛 차트 (발송 분포) | `channels[].sentCount` |

---

## 6. API 61 — GET /api/projects/{projectId}/analytics/segments

### 목적
일정 기간 내 세그먼트별 성과 메트릭 조회.

### 요청

**URL**:
```
GET /api/projects/{projectId}/analytics/segments?from={from}&to={to}
```

### 응답 (HTTP 200 OK)

```json
{
  "success": true,
  "data": {
    "segments": [
      {
        "segmentId": 10,
        "segmentName": "VIP 고객",
        "campaignCount": 3,
        "sentCount": 1000,
        "deliveredCount": 980,
        "clickedCount": 196,
        "deliveryRate": 0.98,
        "clickRate": 0.2
      }
    ]
  },
  "error": null
}
```

### 응답 필드

**segments** (SegmentMetricItem[]):
- `segmentId`, `segmentName`: 세그먼트 식별 정보
- `campaignCount` (long): 이 세그먼트로 발송한 캠페인 수
- `sentCount`, `deliveredCount`, `clickedCount`: 발송 통계
- `deliveryRate`, `clickRate` (double): 비율

---

## 7. API 58 — GET /api/projects/{projectId}/campaigns/{campaignId}/stats

### 목적
특정 캠페인의 누적 통계 (모든 회차 합산). 회차별 상세는 ExecutionStats API에서 조회.

### 요청

**URL**:
```
GET /api/projects/{projectId}/campaigns/{campaignId}/stats
```

### 응답 (HTTP 200 OK)

```json
{
  "success": true,
  "data": {
    "campaignId": 456,
    "totalTarget": 10000,
    "sentCount": 10000,
    "deliveredCount": 9800,
    "clickedCount": 1960,
    "failedCount": 200,
    "deliveryRate": 0.98,
    "clickRate": 0.2,
    "executions": [
      {
        "executionId": 101,
        "status": "COMPLETED",
        "sendStartedAt": "2026-04-01T10:00:00",
        "sentCount": 5000,
        "deliveredCount": 4900,
        "clickedCount": 980
      }
    ],
    "metricsComputedAt": "2026-05-02T14:30:00"
  },
  "error": null
}
```

### 응답 필드

- `campaignId`, `totalTarget`: 캠페인 및 목표 대상자 수
- `sentCount`, `deliveredCount`, `clickedCount`, `failedCount`: 누적 통계
- `deliveryRate`, `clickRate` (double): 평균 비율
- `executions` (ExecutionSummary[]): 회차별 요약
  - `executionId`, `status`, `sendStartedAt`
  - `sentCount`, `deliveredCount`, `clickedCount`
- `metricsComputedAt` (LocalDateTime): 집계 기준 시각

### 프론트엔드 UI 매핑

| 용도 | 응답 필드 |
|---|---|
| 캠페인 상세 KPI | `sentCount`, `deliveryRate`, `clickRate` |
| 회차 목록 + 드릴다운 | `executions` 리스트 (클릭 시 ExecutionStats API 호출) |

---

## 8. NEW API — GET /api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats

### 목적
특정 회차(execution)의 상세 통계. ADR-0002 회차 관리 결과로 추가됨.

### 요청

**URL**:
```
GET /api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats
```

### 응답 (HTTP 200 OK)

```json
{
  "success": true,
  "data": {
    "campaignId": 456,
    "executionId": 101,
    "status": "COMPLETED",
    "totalTarget": 5000,
    "sentCount": 5000,
    "deliveredCount": 4900,
    "clickedCount": 980,
    "failedCount": 100,
    "deliveryRate": 0.98,
    "clickRate": 0.2,
    "sendStartedAt": "2026-04-01T10:00:00",
    "sendCompletedAt": "2026-04-01T10:15:00",
    "metricsComputedAt": "2026-05-02T14:30:00"
  },
  "error": null
}
```

### 응답 필드

- `campaignId`, `executionId`, `status`: 회차 식별 정보
- `totalTarget`: 목표 대상자 수 (SCHEDULED 상태에서는 0)
- `sentCount`, `deliveredCount`, `clickedCount`, `failedCount`: 통계
- `deliveryRate`, `clickRate` (double): 비율
- `sendStartedAt`, `sendCompletedAt` (LocalDateTime): 발송 시간
- `metricsComputedAt` (LocalDateTime): 집계 기준 시각

---

## 9. 오류 응답

| 코드 | HTTP | 설명 |
|---|---|---|
| INVALID_PERIOD | 400 | from > to |
| INVALID_CHANNEL | 400 | 지원하지 않는 채널 값 |
| PROJECT_NOT_FOUND | 404 | projectId 존재하지 않음 |
| CAMPAIGN_NOT_FOUND | 404 | campaignId 존재하지 않음 |
| EXECUTION_NOT_FOUND | 404 | executionId 존재하지 않음 |
| UNAUTHORIZED | 401 | X-Member-Id 헤더 누락 또는 토큰 무효 |
| FORBIDDEN | 403 | 프로젝트 권한 부족 (VIEWER 미만) |
| INTERNAL_ERROR | 500 | 서버 오류 |

---

## 10. 주의사항 & Sprint 6+ 예정

### 10.1 현재 미구현 기능

❌ **대시보드 최근 캠페인 5건**: API 57 응답에 포함되지 않음
- 대안: API 59 (`/analytics/campaigns`)를 별도 호출하여 `page=0&size=5`로 첫 5건 조회

❌ **이탈 세그먼트 KPI**: 요구사항 "이탈 세그먼트" 미구현
- 예정: Sprint 6에서 SegmentMetricItem에 `churnRate` 필드 추가

❌ **시계열 채널별 추이**: API 57의 `dailyTrend`는 전체 통합 시계열만 제공
- 예정: Sprint 5에서 `Map<String, List<DailyPoint>>` 확장 (채널별 세부 추이)

### 10.2 권한 정책

- 클래스 레벨 `@PreAuthorize("@projectPermission.hasRole(#projectId, 'VIEWER')")` 적용
- EDITOR, OWNER, ADMIN은 추가 제약 없이 VIEWER와 동일 접근 가능

### 10.3 메트릭 집계 기준

- `metricsComputedAt`: 최종 갱신 시각 (실시간 집계가 아님)
- 클라이언트는 이 필드로 데이터 신선도 파악 가능

---

## 11. 프론트엔드 통합 가이드 (Sprint 3)

### 11.1 대시보드 페이지 로딩 플로우

1. **컴포넌트 마운트** → `projectId` 로컬스토어에서 읽기
2. **날짜 범위 선택** → "Last 30 days" 등 선택
3. **병렬 API 호출**:
   ```javascript
   Promise.all([
     getDashboard(projectId, { from, to }),
     getChannelMetrics(projectId, { from, to }),
     getSegmentMetrics(projectId, { from, to }),
     getCampaignMetrics(projectId, { from, to, page: 0, size: 5 })
   ])
   ```
4. **렌더링**:
   - KPI 카드 3개 (기간 내 캠페인, 전달률, 오픈률)
   - 라인 차트 (일일 추이)
   - 채널 카드 (채널별 성과)
   - 세그먼트 테이블 (세그먼트별 성과)
   - 최근 캠페인 테이블

### 11.2 캠페인 상세 페이지 (Sprint 4 이후)

- 캠페인 선택 → `getCampaignStats(projectId, campaignId)`
- 회차 클릭 → `getExecutionStats(projectId, campaignId, executionId)`

---

## 12. 참고자료

- **백엔드 구현**: butterfly-reporting (AnalyticsController, AnalyticsService)
- **백엔드 API 규약**: backend/docs/api/06-analytics.md
- **ADR-0002**: 회차(execution) 누적 합산 정책
- ~~**ADR-0003**: 클릭률 분모는 deliveredCount~~ — 2026-04-28 ADR-0007이 supersede
- **ADR-0007**: 분석 집계 테이블 v2 (현재 정의)

---

## 부록 A. ADR-0007 적용 (2026-05-07 갱신)

본 문서의 응답 스키마는 **ADR-0007 supersede 후 정의**를 따른다. 변경 요약:

### A.1 카운트 필드 추가

모든 응답 record에 OPEN/CONVERT 카운트가 추가됐다:

| 응답 | 추가 필드 |
| --- | --- |
| `DashboardResponse.Summary` | `totalOpen`, `totalConvert` |
| `DashboardResponse.DailyPoint` | `opened`, `converted` |
| `CampaignMetricItem` / `ChannelMetricItem` / `SegmentMetricItem` | `openCount`, `convertCount` |
| `CampaignStatsResponse` / `ExecutionSummary` / `ExecutionStatsResponse` | `openCount`, `convertCount` |

### A.2 비율 필드 4종 + nullable

기존 `deliveryRate`, `clickRate` 2종 → **4종으로 확장**:

```text
delivery_rate = delivered / sent
open_rate     = open / delivered      ← 신규
click_rate    = click / open          ← 분모 변경 (delivered → open)
convert_rate  = convert / sent        ← 신규
```

**모든 비율은 분모 0이면 `null`** (백엔드: `Double` Wrapper 직렬화 / 프론트: `number | null`).
KPI 정의서 §3 정책 적용. 화면에서는 `formatRate(value)` 유틸로 일관 `"—"` 표기.

### A.3 ADR-0003 호환성

ADR-0003 시점의 `clickRate = clicked / delivered` 의미를 사용하던 외부 호출자가 있다면 그 정의는 **사라졌다**. 동일 필드명(`clickRate`)을 유지하되 의미만 변경됐으므로 wire-format 차원에서는 깨지지 않지만, 절대값은 다르게 나온다(분모가 작아져 비율이 높아짐).

### A.4 미구현 부분

응답 record 시그니처는 ADR-0007 정합되었지만 `AnalyticsServiceImpl`이 **여전히 Stub**이라 실제 응답은 모든 카운트 0, 모든 비율 null. 진짜 데이터를 채우는 native SQL 작업은 후속 PR(`AnalyticsServiceImpl` 구현 — 5/19 대상).

### A.5 frontend 측 후속

- `Dashboard.tsx` KPI 카드: 4 → 6 (`평균 오픈률`, `평균 전환률` 추가)
- recent campaigns 테이블 컬럼: 5 → 7 (오픈률/전환률 추가)
- `schemas/analytics.ts`: 6종 zod 스키마에 OPEN/CONVERT + nullable rate 적용

자세한 변경 이력은 [`docs/changes/2026-05-07-adr0007-application.md`](../../../../docs/changes/2026-05-07-adr0007-application.md).

