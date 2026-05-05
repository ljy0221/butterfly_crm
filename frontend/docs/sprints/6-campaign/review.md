# Sprint 6 — Campaign CTO 검수 보고서

**작성일**: 2026-05-02
**빌드 상태**: ✅ 0 에러 (재현: `npm run build` 4.67s, 268 modules)
**감사 범위**: 18 신규 + 4 수정 + 4 삭제 + 백엔드 도메인 모델 교차 검증

## Executive Summary
캠페인 도메인은 mock-fallback 인프라 위에 6단계 위저드 + 상세 + 목록을 안정적으로 구축. 빌드 0/0, any 0건, 캠페인 .jsx 잔존 0건. 3대 검수 포인트(Schedule discriminated union, ifMock 양 경로, mapCampaignError jumpStep) 모두 코드 레벨 검증됨. **Sprint 7 진입 가능. Blocker 없음**, P1 follow-up 8건.

## 1. API/DTO 정합성 + Mock 시그니처 — ✅ PASS
- Enum 6종(Channel/CampaignType/CampaignStatus/SendMode/RepeatType/ExecutionStatus) 백엔드 1:1
- Schedule 직렬화: scheduleToBackend() immediate/scheduled/recurring → sendMode + scheduledAt + repeat* 매핑
- toUtcIso 변환 + Z-suffix 가드(이중 변환 방지)
- ⚠️ api/campaign.ts:208,251 real path body가 `scheduleToBackend(schedule)` + `schedule: req.schedule` 둘 다 전송 — 백엔드 DTO 확정 후 정리

## 2. Mock-fallback 인프라 — ✅ PASS
- 7+1 ifMock 양 경로 (campaign 7 + analytics getCampaignStats)
- Seed 8건 (Status 4종 × Channel 5종 × SendMode 3종 모두 커버)
- 상태 전이 가드 (CAMPAIGN_ALREADY_ACTIVE 등)
- READY → SEND → SUCCESS 시뮬 (0.5s/1.5s setTimeout)
- ⚠️ localStorage 영속화 P2 (휘발성)

## 3. 위저드 UI/UX — ✅ PASS
- 6단계 + useReducer (type 변경 시 새 객체 교체로 union 일관성)
- 부분 검증 (step1~5Schema safeParse)
- jumpStep 자동 회귀 (mapCampaignError 14개 코드 모두 jumpStep 부착)
- AUTOMATION P1 disabled, SMS/WEBHOOK/KAKAO P1 뱃지(선택은 가능)
- datetime-local + min 가드 (현재+60s)
- RECURRING cron 미리보기 (aria-live, RecurringCronGenerator 포팅)
- 단일 채널 강제
- Step 5 채널 필터 (Sprint 4 useTemplateListQuery 재사용)
- Step 6 ReviewSection [편집] → onJump

## 4. 권한 + 액션 매트릭스 — ✅ PASS
- VIEWER /new redirect
- DRAFT [수정][삭제][시작] / ACTIVE [일시정지] / ENDED 모든 액션 비활성
- confirmDialog tone="danger" 일관 (Sprint 2.5 키)

## 5. Sprint 5 Segment 연동 — ✅ PASS
- getLinkedCampaignsMock → getCampaignsBySegmentId 4-tab 그룹화
- 단방향 import (segment → campaign)
- SegmentBuilder linked-campaigns placeholder 활성화

## 6. 회귀/빌드 — ✅ PASS
- npm run build 0 에러 (268 modules, 기존 chunk size 경고만)
- any 0건
- Campaigns.jsx 등 4개 .jsx 모두 삭제 확인
- Sprint 1~5 회귀 없음

## 7. 백엔드 통합 Readiness — ⚠️ WARN
- 단일 플래그 토글 PASS (`campaign: true → false` 한 줄)
- analytics 분기 PASS
- ⚠️ DateTime ISO timezone (Sprint 5 P0와 동일) — Sprint 7 컨트롤러 구현 시 ZonedDateTime 결정 필수
- ⚠️ recurring nextRunAt: "" → 클라가 첫 실행 산출 필요 (백엔드 통합 시)

## Sprint 7 진입 전 Blocker
**없음**.

## Sprint 6 Follow-up (Sprint 7 동시 처리)

| 우선 | 항목 |
|---|---|
| P1 | _mock/segment.ts:805 주석 방향 정정 |
| P1 | api/campaign.ts real path body schedule 중복 정리 (백엔드 DTO 확정 후) |
| P1 | recurring nextRunAt 클라 산출 |
| P1 | ACTIVE 폴링: useCampaignDetailQuery refetchInterval 활성화 |
| P2 | mock localStorage 영속화 |
| P2 | PAUSED → ACTIVE 재개 mutation UI |
| P3 | AUTOMATION 활성화 (Sprint 8 Journey 합류 시) |

## 다음 Sprint 권장
**Sprint 7 옵션 A**: Campaign Backend Activation — butterfly-campaign 모듈 Service/Controller 신설, 7 endpoints 구현, 단일 플래그 토글로 회귀 테스트
**Sprint 7 옵션 B**: 이벤트 모니터링 + 알림 로그 + 감사 로그 도메인 (요구사항 명세상 P0)
- 사용자 결정에 따라 진행
