# Sprint 3 — Analytics(대시보드) UI 설계

> UI/UX Agent. Backend Liaison `docs/sprints/3-analytics/api.md` + Sprint 1~2 ui.md/review.md + 기존 공용 컴포넌트(`src/components/common`) + 기존 mock `Dashboard.jsx` 기준.
>
> **작성**: 2026-05-01
> **전제**: VIEWER 이상 모두 조회. KPI/시계열은 `/analytics/dashboard` 단일 호출. 최근 캠페인 5건은 `/analytics/campaigns?page=0&size=5`로 분리 호출. 기존 `Dashboard.jsx` 골격(SVG 차트·StatCard×4·테이블) 보존하면서 `.tsx`로 변환·실 데이터 매핑.

---

## 1. 라우트 매핑

| 경로 | 페이지 | 가드 | 상태 |
|---|---|---|---|
| `/p/:projectId/dashboard` | `Dashboard` (.tsx 신규) | `RequireAuth` + `RequireProject` + `AppLayout` | placeholder → 교체 |
| `/p/:projectId/analytics` | `Analytics` (placeholder 유지) | 동일 | 그대로 |

**결정**: Sprint 3은 **Dashboard만 실 API 연동**. `/analytics`는 캠페인 페이지네이션·채널 비교·세그먼트 메트릭이 더 깊은 UX 설계를 필요로 해 Sprint 4 이후로 이월.

---

## 2. 페이지 와이어프레임

### 2.1 Dashboard (`/p/:projectId/dashboard`)

```
+-------------------------------------------------------+
| PageHeader: 대시보드 / 최근 30일 · YYYY.MM.DD–MM.DD   |
|             [최근 30일 ▾] [+ 새 캠페인]               |
+-------------------------------------------------------+
| [기간 캠페인][총 발송][평균 전달률][평균 오픈률]      | StatCard×4
+-------------------------------------------------------+
| [발송 추이 (SVG)]           | [채널별 발송 비중]      | 2fr : 1fr
+-------------------------------------------------------+
| [최근 캠페인 5건 테이블]    | [빠른 작업 4개]         | 2fr : 1fr
+-------------------------------------------------------+
```

#### 2.1.1 PageHeader
- 우측 actions: `[최근 30일 ▾]` (Menu dropdown — 7/30/90일), `[+ 새 캠페인]` (EDITOR 이상에서만 노출)

#### 2.1.2 KPI 카드 4개

요구사항 라벨(활성 고객/오픈률/전환 매출/이탈 세그먼트)은 백엔드 미매핑이 3건. **권장: 백엔드 즉시 매핑 가능한 4종으로 출시**:

| 라벨 | 출처 |
|---|---|
| 기간 내 캠페인 | `summary.campaignsSentInPeriod` |
| 총 발송 | `summary.totalSent` (천 단위 콤마) |
| 평균 전달률 | `summary.avgDeliveryRate * 100` (%) |
| 평균 오픈률 | `summary.avgClickRate * 100` (%) |

> 활성 고객/전환 매출/이탈 세그먼트는 review.md follow-up으로 백엔드 필드 확장 시 교체.

StatCard는 `isLoading` prop 없으므로 wrapper에서 skeleton 4개로 처리.

#### 2.1.3 채널별 발송 추이 차트
- `Card title="발송 추이"` + 우상단 `<Tag dot>전송</Tag><Tag dot>전달</Tag><Tag dot>클릭</Tag>`
- 데이터: `dashboard.dailyTrend[]` (`{date, sent, delivered, clicked}`)
- 채널 분리는 ❌ Sprint 5 → 라인을 sent/delivered/clicked 3개로 재구성
- `buildLinePath(points, height, maxY)` 헬퍼로 SVG path d 동적 생성. 기존 `#gBlue` gradient 보존.
- 빈 상태: `dailyTrend.length === 0` → "이 기간에는 발송 데이터가 없습니다"

#### 2.1.4 채널별 발송 비중
- `/analytics/channels`로 별도 호출 (병렬). `channels[].sentCount` 합계 비율 → 진행바 4개(FCM/이메일/SMS/웹훅)

#### 2.1.5 최근 캠페인 5건 테이블
- `getCampaignMetrics(projectId, {from, to, page:0, size:5})`
- 컬럼: 캠페인명 / 채널(ChannelBadge) / 발송수 / 오픈률 / 전달률
- 행 클릭 → `/p/:pid/campaigns/:campaignId` (Sprint 6 placeholder)
- 빈 상태: "최근 발송한 캠페인이 없습니다" + EDITOR 이상이면 [캠페인 만들기] CTA
- 카드 우상단 `전체 보기 →` → `/p/:pid/campaigns`

#### 2.1.6 빠른 작업 (사이드)
- 4개 항목 (세그먼트/캠페인 만들기/이벤트 스키마/성과 리포트) — 모두 placeholder navigate
- VIEWER는 "세그먼트/캠페인 만들기" dim + tooltip "EDITOR 권한 필요"

---

### 2.2 Analytics (`/p/:projectId/analytics`)
**Sprint 3 범위 외**. placeholder의 `sprint="Sprint 3"` → `sprint="Sprint 4"`로만 갱신.

---

## 3. 폼/필터 (날짜 범위)

- 기본값: `to=today`, `from=today-30d` (Asia/Seoul, dayjs)
- PageHeader 버튼 클릭 → Menu dropdown (7/30/90일). 사용자 정의는 P1.
- 페이지 local state. URL 동기화 X.
- 변경 시 dashboard/channels/recentCampaigns 모두 refetch (queryKey에 `{from, to}` 포함)

---

## 4. 권한별 차이
- 조회: VIEWER 이상 모두
- 액션 노출 (PageHeader 새 캠페인, 빠른 작업, 빈 상태 CTA): EDITOR 이상만 — inline `role !== "VIEWER"` 분기

---

## 5. 빈/로딩/에러 상태
- 로딩: 카드 단위 skeleton (StatCard 4개, 차트 점선 grid + caption, 테이블 5행)
- 에러: 카드 단위 "데이터를 불러오지 못했습니다 [재시도]". 전역 toast 발생시키지 않음(부분 실패 허용)
- INVALID_PERIOD: 자체 클라 검증으로 차단

---

## 6. 접근성
- 차트 SVG `role="img"` + 동적 aria-label
- StatCard `role="figure"` + aria-labelledby
- 테이블 `role="table/row/cell"` 명시
- 행 키보드 `tabIndex={0}` + Enter

---

## 7. Frontend Agent 노트

### 7.1 신규 파일
- `src/api/analytics.ts` — getDashboard, getCampaignMetrics, getChannelMetrics, getCampaignStats (Sprint 6 대비 함수만 미리 정의)
- `src/hooks/queries/analytics.ts` — useDashboardQuery, useChannelMetricsQuery, useRecentCampaignsQuery
- `src/schemas/analytics.ts` — zod 스키마
- `src/features/dashboard/Dashboard.tsx` — 기존 .jsx 교체
- `src/utils/analytics.ts` — pctOfDelivered, formatPeriod, dailyTrendToPath

### 7.2 라우터 변경
- `/p/:projectId/dashboard` element → `<Dashboard />`
- `/analytics` placeholder sprint 라벨 갱신

### 7.3 컴포넌트 재사용
- StatCard, ChannelBadge, Card, Tag, Button, Icon, PageHeader 모두 그대로
- 차트 라이브러리 도입 X — 인라인 SVG, path 동적 생성

### 7.4 후속 진입점
- 캠페인 행 → `/p/:pid/campaigns/:id` (Sprint 6)
- 빠른 작업 → segments/new(5), campaigns/new(6)

---

## 8. 위험 & follow-up

1. KPI 라벨 합의 필요 — 권장 4종(기간 캠페인/총 발송/전달률/오픈률)으로 1차 출시
2. `/analytics/campaigns` 정렬 기본값 미명시 — Backend Liaison에 `sort=createdAt,desc` 또는 `sentCount,desc` 보장 요청
3. previousPeriod 비교 데이터 부재 — delta 생략(P1)
4. 채널별 시계열 ❌ → Sprint 5 백엔드 확장 시 라인 재구성

---

**버전**: 1.0
**다음 단계**: Frontend Agent 구현 → CTO 검수
