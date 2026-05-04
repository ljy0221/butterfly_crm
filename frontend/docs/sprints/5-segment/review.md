# Sprint 5 — Segment CTO 검수 보고서

**작성일**: 2026-05-01
**빌드 상태**: ✅ 249 modules 0 에러 (직접 재현)
**감사 범위**: 19파일 (FE) + 백엔드 sealed interface/Operator/Period/BuildStatus/SegmentErrorCode 교차 검증

## Executive Summary

백엔드 sealed interface 5종 + Period 3종 + Operator 8종 + BuildStatus 4종 + SegmentErrorCode 9종과 모두 1:1 정합. 트리 어댑터(`toBackendTree`/`fromBackendTree`)는 백엔드 ConditionNodeDeserializer의 `logic` 키 분기 규약과 정확히 동기화되어 round-trip 안전. 즐겨찾기 낙관적 업데이트는 표준 3단 패턴(onMutate cancel+snapshot, onError 롤백, onSettled invalidate). **Sprint 6 진입 가능(GO)**, 단 P0 follow-up 2건(EventFilter value round-trip, timezone 합의) Sprint 6 첫 주 내 정리 권장.

## 1. API/DTO 정합성 + 트리 직렬화 — ✅ PASS
- ConditionNode sealed 5종, Operator 8종, LogicOp, Period 3종, BuildStatus, SegmentErrorCode 9종 모두 1:1
- `toBackendTree`: GroupNode `type:"group"` 디스크리미네이터 제거 후 `{logic, conditions}` — 백엔드 `tree.has("logic")` 분기 일치
- `fromBackendTree`: `"logic" in node` 로 group 판정 — Jackson serializer가 GroupNode에 type 미부착이라 정합
- Tag/Device op 호환성(eq/neq/in/contains, eq/neq/in) 일치
- ⚠️ `EventFilter.value` round-trip: op="in" 콤마 토큰화 시 사용자가 콤마 포함 입력 잘림 / op="gt" 빈문자→숫자 강제 부재 (P1)

## 2. UI/UX — ✅ PASS (WARN 1건)
- view↔edit 토글 (BUILDING 시 [수정] disabled)
- 저장 활성: previewCallCount >= 1 + 트리 비공백, view→edit 전환 시 detail.estimatedAt 있으면 카운트 1 초기화
- dirty 추적 + beforeunload + confirmDialog
- 미리보기 dirty 경고
- ⚠️ 깊이 가드 어긋남: `MAX_DEPTH=3`이지만 path.length=3(4중첩)까지 허용 — UI 카피 "최대 3단계"와 불일치 (P1)
- 즐겨찾기 낙관적 업데이트: getQueriesData로 페이지/필터별 list 캐시 일괄 패치
- ConfirmDialog 키 정합 (Sprint 2.5)

## 3. 권한 가드 — ✅ PASS
- VIEWER → /new redirect
- view 모드 isFormDisabled prop 전체 전파
- 인라인 `role !== "VIEWER"` 일관
- 매칭 사용자/연결 캠페인 placeholder + 빈 mock

## 4. 보안/안정성 — ✅ PASS (WARN 1건)
- 즐겨찾기 race: cancelQueries + onSettled invalidate
- preview 501/4xx 분리
- ⚠️ router-level dirty guard 부재: `onBack` 시 dirty 확인 없이 navigate (P1, useBlocker 권장)
- localStorage/sessionStorage 미사용

## 5. 회귀/빌드 — ✅ PASS
- npm run build 0 에러 (249 modules)
- any 0건 (segment 도메인)
- Sprint 1~4 회귀 없음 (templates ifMock, ModalHost ConfirmOptions 일관)
- Templates.jsx/SegmentBuilder.jsx 잔존 정리 확인
- index.d.ts Icon 시그니처 완화 부작용 없음 (모든 호출부 ReactNode 반환만 사용)

## 6. 백엔드 통합 readiness — ⚠️ WARN
- Phase 6/7/8 mock 플래그 false 전환 가능
- 트리 어댑터 안정성 (단일 함수 격리)
- ⚠️ **estimatedAt timezone (P0)**: 백엔드 `LocalDateTime` (no offset) → 브라우저 로컬 TZ 해석으로 N시간 드리프트. `OffsetDateTime` 전환 또는 `spring.jackson.time-zone=Asia/Seoul` 명시 필요
- ⚠️ rebuild 폴링: 현 5.5s setTimeout → 실 API 시 BUILDING 길어질 수 있어 refetchInterval 또는 SSE 필요 (P1)
- ⚠️ linked-campaigns Sprint 6 mock store 연동 (P1)

## Sprint 6 진입 전 Blocker
**없음**.

## Sprint 5 follow-up

| # | 우선 | 항목 | 담당 |
|---|---|---|---|
| 1 | P0 | EventFilter value round-trip 통합 테스트 | Frontend + Backend |
| 2 | P0 | estimatedAt timezone 합의 (OffsetDateTime 또는 TZ 명시) | Backend |
| 3 | P1 | 깊이 가드 산식/카피 정렬 | Frontend |
| 4 | P1 | rebuild 폴링 도입 (BUILDING 동안 refetchInterval) | Frontend |
| 5 | P1 | router-level dirty guard (useBlocker) — onBack confirmDialog | Frontend |
| 6 | P2 | Icon 시그니처 정밀 타입 회복 | Frontend |

## 다음 Sprint 권장
- **Sprint 6 (캠페인)**: linked-campaigns mock store와 `useLinkedCampaignsQuery` 연결을 첫 PR로 → segment view 모드 placeholder 활성화
- **Sprint 7 (이벤트)**: `event_categories` mock 해제 → `EventNodeEditor.COMMON_CATEGORIES` → 백엔드 카테고리 카탈로그 API로 교체 + SEGMENT_501_EVENT_NOT_READY 자연 소멸
- 테스트: `toBackendTree`/`fromBackendTree` 단위 테스트 + `parseFilterValue` 회귀 픽스처
