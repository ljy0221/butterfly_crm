# Sprint 4 — Template CTO 검수 보고서

**작성일**: 2026-05-01
**빌드 상태**: ✅ 224 modules 0 에러

## Executive Summary

POST 실 API + GET/PUT/DELETE mock-fallback 구조. Frontend 산출물은 명세와 1:1 정합, mock 봉합·escapeHtml·zod 검증·React Query 키 위생 견고. **Sprint 5 진입 Blocker 없음**. Backend `X-Member-Id` 헤더 수신 미처리, channel enum 미적용은 P0 follow-up (백엔드 담당).

## 1. API/DTO 정합성 — ✅ PASS (조건부)
- MessageTemplateCreateRequest/Response 1:1 일치
- ⚠️ memberId: 프런트 X-Member-Id 헤더 자동 첨부, 백엔드 컨트롤러 `@RequestHeader` 미부착
- ⚠️ channel String → enum 검증 백엔드 부재
- ⚠️ 변수 정규식 차이: 백엔드 `\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}` vs 프런트 `\{\{(\w+)\}\}` (공백/식별자 첫글자 케이스 차이)

## 2. Mock-fallback 인프라 — ✅ PASS
- 모든 GET/PUT/DELETE에 ifMock 적용
- POST 봉합 흐름 정확 (실 API 응답 → mockUpsertTemplate)
- seed 6건 (5채널 모두 + ARCHIVED)
- delay 적용 (180~220ms)
- 모듈 싱글톤 ESM 보장, PK 충돌 차단 (nextId = id+1)
- ⚠️ 새로고침 초기화 (localStorage 영속화 P1)

## 3. UI/UX — ✅ PASS
- 카드 그리드 반응형 (auto-fill minmax 280px)
- 변수 강조 (정규식 차이는 별도 이슈)
- 권한 분기 일관 (role !== "VIEWER")
- 빈/로딩/에러 카드 단위 분리
- 모달 role="dialog" + autoFocus + aria-describedby
- ConfirmDialog 키 정합성 (Sprint 2.5 수정 적용)

## 4. 보안/안정성 — ✅ PASS
- VariableHighlight: escapeHtml + dangerouslySetInnerHTML 안전 (입력 → escape → 매칭 → 안전 span)
- ConfirmDialog tone="danger" 일관
- mock store 메모리 누수 없음

## 5. 회귀/빌드 — ✅ PASS
- npm run build 0 에러 (224 modules)
- any 0건
- ⚠️ Templates.jsx 잔존 (라우팅 분리 후 dead file)

## 6. 백엔드 통합 readiness — ✅ PASS (조건부)
- ifMock 양 경로 작성 — 플래그 한 줄 변경으로 실 API 전환
- description/title 옵셔널 처리 — 백엔드 미저장 시 UI 안 깨짐
- ⚠️ 백엔드 미저장 description/title은 mock 봉합으로 세션 한정 보존

## Sprint 5 진입 전 Blocker
**없음**.

## Sprint 4 follow-up

| 우선 | 항목 | 담당 |
|---|---|---|
| **P0** | 백엔드 컨트롤러 `@RequestHeader("X-Member-Id")` 부착 | Backend |
| **P0** | 백엔드 channel enum/@Pattern 검증 | Backend |
| **P1** | Templates.jsx 삭제 | Frontend |
| **P1** | 변수 정규식 백엔드와 정렬 | Frontend |
| **P1** | description/title 백엔드 컬럼 추가 | Backend |
| **P2** | mock store localStorage 영속화 | Frontend |
| **P2** | escapeHtml에 `'/"` 추가 (defense-in-depth) | Frontend |

## 다음 Sprint 권장
- Sprint 5 (segment) 진입과 동시에 Backend가 GET/PUT/DELETE + X-Member-Id 헤더 + channel enum 처리 → 플래그 false 전환만으로 실 API 전환
- VariableHighlight XSS payload 단위 테스트 1건 추가
- Sprint 5 권한 도메인 작업 시 templates 5개 엔드포인트 `@PreAuthorize` 일괄 적용
