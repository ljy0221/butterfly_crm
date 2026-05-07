# ADR-0007 적용 — 분석/리포트 응답 record OPEN/CONVERT 확장 (2026-05-07)

> 작성: 역할 6 (분석/대시보드 집계, 통합 검증) — 이재영
> 영향 범위: butterfly-reporting (DTO + Stub) · frontend (analytics 도메인 화면 + zod)
> 머지 브랜치: `Feat/S14P31B101_Adr0007Application` → develop
> 관련 ADR: [ADR-0007 분석 집계 테이블 v2](../../backend/docs/adr/0007-analytics-aggregation-tables-v2.md) (2026-04-28 supersedes ADR-0003)
> 관련 검수: 위 PR 흐름의 P0-3 (KPI 라벨/정의 충돌)

## 1. 한 줄 요약

ADR-0007이 **분석 집계 테이블·SQL 단계**에서만 적용되어 있었고, **응답 record(API 57~61) + 프론트 KPI 표시**는 ADR-0003 시점 형태로 박제되어 있었음 — 두 계층의 정의를 ADR-0007 쪽으로 통일.

## 2. 무엇이 바뀌었나

### 2-1. 백엔드 응답 record 6종 (`butterfly-reporting/dto/`)

| record | 신규 카운트 필드 | 비율 타입 변경 | clickRate 분모 변경 |
| --- | --- | --- | --- |
| `DashboardResponse.Summary` | `totalOpen`, `totalConvert` | `double → Double` (4종) | clicked/delivered → click/open |
| `DashboardResponse.DailyPoint` | `opened`, `converted` | — | — |
| `CampaignMetricItem` | `openCount`, `convertCount` | `double → Double` (4종) | 동일 |
| `ChannelMetricItem` | `openCount`, `convertCount` | `double → Double` (4종) | 동일 |
| `SegmentMetricItem` | `openCount`, `convertCount` | `double → Double` (4종) | 동일 |
| `CampaignStatsResponse` + `ExecutionSummary` | `openCount`, `convertCount` | `double → Double` (4종) | 동일 |
| `ExecutionStatsResponse` | `openCount`, `convertCount` | `double → Double` (4종) | 동일 |

신규 비율 필드 4종 = `deliveryRate`, `openRate`, `clickRate`, `convertRate` (이전 2종에서 확장).

### 2-2. 백엔드 서비스 (`AnalyticsServiceStub`)

여전히 Stub 상태(`AnalyticsServiceImpl` 미구현). 신규 필드는 `0L` / `null`로 채워서 컨트롤러는 200 OK 정상 응답. 실제 데이터 채우는 native SQL은 후속 작업으로 분리.

### 2-3. 프론트 (`frontend/src/`)

| 파일 | 변경 |
| --- | --- |
| `schemas/analytics.ts` | 6종 zod 스키마에 OPEN/CONVERT 필드 + `rate: number \| null` 적용 |
| `api/analytics.ts` | `CampaignStatsResponse` / `ExecutionStatsResponse` / `ExecutionSummary` 인터페이스 갱신, mock fallback 응답 정합 |
| `api/_mock/campaign.ts` | `getCampaignStatsMock`이 OPEN/CONVERT 카운트와 4종 비율 반환 (시연용 합성 비율: open=70% of delivered, click=30% of open, convert=8% of sent) |
| `features/dashboard/Dashboard.tsx` | KPI 카드 4 → 6 (`평균 오픈률`/`평균 전환률` 추가), recent campaigns 테이블 컬럼 5 → 7, `RateCell` 헬퍼 추출 |

## 3. 왜 바꿨나

- **ADR-0007 supersede 사실이 record에 미반영**: aggregation 모듈(`CampaignStatsDailyRepository.java:46-49`)에서는 `click_rate = click/open`, 분모 0 → NULL이 적용된 SQL이 native UPSERT를 돌고 있었지만, API 응답 record는 `double clickRate`(non-null, clicked/delivered 의미)로 박제 — 두 계층이 같은 컬럼을 다른 정의로 부르고 있었음.
- **시연 모순**: 펀널 페이지(S14P31B101-90)는 5단계(SENT→DELIVERED→OPEN→CLICK→CONVERT)를 보여주지만, 대시보드 KPI는 4종(발송/전달/오픈?/클릭?)이라 첫 화면이 5단계를 받쳐주지 못함.
- **검수 P0-3 / P0-4**: "평균 오픈률" 라벨이 실제 데이터로는 `avgClickRate`였고, `null * 100 = NaN%` 위험.

## 4. 영향 받는 곳 / 대응

### 4-1. 다른 도메인 모듈 / 외부 사용처

`grep`으로 전 모듈 확인 결과 6개 record는 **butterfly-reporting 외부에서 사용처 0건**. 안전하게 시그니처 확장 가능. 외부 호출자는 wire-format 차원에서 신규 필드 무시 — non-breaking.

### 4-2. 실제 데이터 흐름

현재 컨트롤러 응답은 Stub이 채우는 0/null. `AnalyticsServiceImpl`이 구현되면(별도 PR 예정):

- daily 집계 테이블(`campaign_stats_daily`, `channel_stats_daily`, `segment_stats_daily`, `project_stats_daily`)에서 직접 SELECT
- 비율은 SQL에서 `CASE WHEN denominator = 0 THEN NULL ELSE n/d END` 처리 후 그대로 반환 (Java 단계에서 추가 변환 X)
- hourly 카드는 별도 — 본 PR 범위 밖

### 4-3. 다른 도메인 영향

- **캠페인 도메인 (mock-only)**: `getCampaignStatsMock`만 정합 갱신, mock 시연이 정상 동작
- **세그먼트 도메인**: `SegmentMetricItem`은 reporting이 소유 — 세그먼트 도메인은 자기 record 따로 보유
- **자동화 플로우 / 안드로이드 SDK**: 무관

## 5. 5/15 Code Freeze 영향

- 본 PR 머지로 끝나는 변경 — freeze 통과 시 추가 작업 0
- 후속 `AnalyticsServiceImpl` 구현은 **freeze 범위 밖**이지만, 5/22 발표 dry-run에서 진짜 데이터를 보여주려면 5/19~21 사이 머지 권장

## 6. 5/22 발표 영향

대시보드 KPI 6종이 펀널 5단계와 자연 정합 — 시연 흐름:
1. 분석 허브(`/analytics`) — 4 카드 (대시보드/펀널/매트릭스/감사로그)
2. 대시보드 — 6 KPI (발송→전달→오픈→클릭→전환의 평균 비율) + 추이 + 채널 + 최근 캠페인 7컬럼
3. 펀널 — 5단계 가로 막대
4. 매트릭스 — 세그먼트×채널 OWNER 전용

KPI 정의 질문(`이 38%는 어떻게 계산되나요?`)에 record/SQL/화면 모두 한 정의로 답할 수 있음.

## 7. 후속 작업 — 누가 무엇을

| 책임자 | 작업 | 시점 |
| --- | --- | --- |
| 역할 6 (분석) | `AnalyticsServiceImpl` native SQL — daily 4종 테이블 SELECT | 5/19까지 (발표 dry-run 전) |
| Aggregation Auditor | hourly 카드 응답에 OPEN/CONVERT 추가 검토 (`campaign_stats_hourly`) | post-MVP |
| Backend Liaison | `getCampaignMetrics` sort 기본값 합의 (현재 프론트 default `createdAt,desc`) | 5/14 |
| Spring Architecture Inspector | 신규 필드 추가에 대한 ArchUnit baseline 영향 검사 | 본 PR CI에서 자동 |
| 캠페인 도메인 | `getCampaignStatsMock` 외 mock 핸들러 영향 점검 | 본 PR 머지 후 본인 빌드만 확인 |

## 8. 변경된 파일 (전체 목록)

### 백엔드 (8 파일)

```
backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/dto/
  ├── DashboardResponse.java                  (Summary record 4→7 카운트, 2→4 비율, DailyPoint 3→5)
  ├── CampaignMetricItem.java                 (8→13 필드)
  ├── ChannelMetricItem.java                  (7→11 필드)
  ├── SegmentMetricItem.java                  (8→12 필드)
  ├── CampaignStatsResponse.java              (8→12 필드 + ExecutionSummary)
  └── ExecutionStatsResponse.java             (10→14 필드)
backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/service/
  └── AnalyticsServiceStub.java               (신규 필드 0L/null 반환)
```

### 프론트 (5 파일)

```
frontend/src/schemas/analytics.ts             (6종 zod 스키마 OPEN/CONVERT 추가)
frontend/src/api/analytics.ts                 (CampaignStatsResponse/ExecutionStatsResponse 인터페이스 + mock fallback 정합)
frontend/src/api/_mock/campaign.ts            (getCampaignStatsMock 합성 비율 4종)
frontend/src/features/dashboard/Dashboard.tsx (KPI 4→6, recent table 5→7, RateCell 추출)
```

### 신규 문서 (본 파일)

```
docs/changes/2026-05-07-adr0007-application.md
```

## 9. 변경 검증

- **백엔드**: `./gradlew :butterfly-reporting:compileJava` ✅, `./gradlew :butterfly-reporting:test -x integrationTest` ✅
- **프론트**: `npm run type-check` ✅
- **외부 사용처**: butterfly-reporting 외 0건 (`grep`으로 검증)

## 10. 전파 채널

- 본 문서 — `docs/changes/` 폴더에 보관 (모든 도메인 공유)
- Jira 코멘트 — `S14P31B101-76` (KPI 정의서) + 검수 리포트 P0-3
- Slack — 본 PR MR 링크 + 위 §2~§4 요약본

## 11. 변경 히스토리

| 날짜 | 작성자 | 내용 |
| --- | --- | --- |
| 2026-05-07 | 이재영 (역할 6) | 초판 — record 시그니처 + 프론트 KPI 정합 PR 머지 직전 |
