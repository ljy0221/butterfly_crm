# Sprint 3 — Analytics CTO 검수 보고서

**작성일**: 2026-05-01
**빌드 상태**: ✅ 219 modules 0 에러

## Executive Summary

Sprint 3 Analytics 도메인은 **백엔드 6개 record 모두와 1:1로 정합**, 차트 헬퍼 `dailyTrendToPath`는 빈 배열·단일 포인트·divisor=0·NaN을 모두 안전하게 방어. 권한 가드는 `RequireProject`(URL) + `role !== "VIEWER"`(액션 인라인) 2계층. **Sprint 4 진입에 차단 사항 없음**. follow-up 10건 (Dashboard.jsx 잔존, schemas/analytics.ts 미생성, getSegmentMetrics/getExecutionStats 함수 미정의, nullish 가드 등).

## 1. API/DTO 정합성 — ✅ PASS
- DashboardResponse/Summary, CampaignMetricItem, ChannelMetricsResponse, CampaignStatsResponse, PageResponse(record `page` 필드) 모두 백엔드 record와 1:1
- LocalDate yyyy-MM-dd 직렬화 일치
- ⚠️ getSegmentMetrics, getExecutionStats 함수 미정의 (Sprint 6 대비 누락)

## 2. UI/와이어프레임 — ✅ PASS
- KPI 4종 (campaignsSentInPeriod/totalSent/avgDeliveryRate/avgClickRate) 일치
- 채널별 발송 비중: 단일 채널도 동적 렌더 정상
- VIEWER 액션 차단 일관 (`canEdit` 단일 변수)
- skeleton/empty/error 카드 단위 정상
- ⚠️ `formatPeriod` 표기 (`YYYY.MM.DD – YYYY.MM.DD`) — ui.md는 단축형(MM.DD) 권장

## 3. 차트 안정성 — ✅ PASS
- 빈 배열, 단일 포인트, yMax=0 모두 방어
- 3 라인 동일 yMax 정규화 (외부 계산 후 sent/delivered/clicked 주입)
- ⚠️ `dailyTrendToPath:76` `p[key]`가 null일 때 NaN 발생 가능 — `Number(p[key]) || 0` 가드 권장

## 4. 권한 가드 — ✅ PASS
- URL 직접 접근: RequireProject가 차단
- 인라인 `role !== "VIEWER"` 단일 변수로 5곳 통일
- 백엔드 `@PreAuthorize("VIEWER")` 클래스 레벨

## 5. 회귀 / 빌드 — ✅ PASS
- npm run build 0 에러 (219 modules)
- any 0건
- ⚠️ `as string` 캐스트 2건 (Dashboard.tsx:278,280 — utils/fmt.js가 .js라 추론 한계)
- ⚠️ schemas/analytics.ts 미생성 (ui.md §7.1 약속 위반)
- ⚠️ Dashboard.jsx 잔존 (mock import 그래프 미참조이나 파일 보존)
- Sprint 1/2 회귀 없음

## 6. 보안 / UX — ✅ PASS
- 응답 모두 집계 메트릭, PII/credential 0
- Race condition 안전 (queryKey에 from/to 포함)
- 부분 실패 격리 (3개 useQuery 독립)
- ⚠️ INVALID_PERIOD 클라 검증 부재 (현재 dropdown만 있어 사용자 정의 기간 도입 시 필수)

---

## Sprint 4 진입 전 Blocker
**없음**.

## Sprint 3 follow-up (우선순위)

1. **(우선)** `Dashboard.jsx` 삭제
2. **(우선)** `dailyTrendToPath:76` nullish 가드 추가
3. `schemas/analytics.ts` zod 추가
4. `getSegmentMetrics` + 타입 정의 추가
5. `getExecutionStats` 함수·타입 추가 (Sprint 6 대비)
6. `utils/fmt.js` → `fmt.ts` 마이그레이션
7. `formatPeriod` 단축형 검토
8. `/analytics/campaigns` 정렬 기본값 합의 (Backend Liaison)
9. KPI delta(이전 기간 대비) Backend `previousPeriod` 필드 추가 후
10. `RecordPage`/`BackendPage` 공통 alias 통합

## 다음 Sprint 권장

- **Sprint 4(템플릿)** 우선 진입. 단, Sprint 3 follow-up #1, #2 즉시 처리 권장
- Sprint 5에서 백엔드 `Map<String, List<DailyPoint>>` 확장 시 차트 채널별 재구성
- zod 스키마는 Sprint 4 시작 시 analytics 응답 4종 일괄 추가
