# Sprint 2 — Tenant CTO 검수 보고서

**작성일**: 2026-05-01
**검수 대상**: Sprint 2 산출물 (tenant 도메인, 신규 8 / 수정 2 파일)
**빌드 상태**: ✅ 217 modules 0 에러

## Executive Summary

타입·DTO·에러 매핑은 백엔드 record와 정확히 합치하고 React Query 캐시 키 구조도 mutation invalidate 흐름과 정합한다. 다만 **두 곳의 redirect 루프 위험**(ProjectSelect의 useEffect 의존성, SettingsLayout의 store 동기화)과 **ConfirmDialog 메시지 누락(Sprint 1 회귀 표면화)**, **API 키 모달이 Esc/배경 클릭으로 즉시 사라져 평문이 영구 손실되는 UX**가 보안·UX 관점의 실질적 Blocker. Frontend Agent가 명시적으로 미해결로 둔 ModalHost `dismissible: false`는 더 이상 미룰 수 없는 시점이다.

---

## 1. API/DTO 정합성

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| `ProjectResponse` 필드 일치 | ✅ PASS | 백엔드 record와 1:1 (`api/tenant.ts:18-28`) |
| `ProjectMemberResponse` LocalDate | ✅ PASS | ISO 문자열 직렬화와 일치 |
| `UpdateMemberRoleRequest` | ✅ PASS | Zod로 OWNER 옵션 사전 차단 |
| `ApiKeyResponse` 1회 평문 강제 | ⚠️ WARN | branded type 미도입, 단순 string |
| `getProject` Long → number | ⚠️ WARN | 현재 안전, 향후 ID 증가 시 위험 |
| `Role` 타입 분기 | ⚠️ WARN | `api/types.ts:44`(3개) vs `schemas/tenant.ts:17`(4개) 이중 enum |

## 2. 에러 코드 매핑

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| PROJ_002~008 매핑 | ✅ PASS | 7개 코드 모두 매핑 (tenant-errors.ts:27-33) |
| PROJ_007/008 시나리오 | ✅ PASS | UI에서 사전 차단 + 서버 백업 |
| PROJ_006 invalidate | ✅ PASS | TENANT_REFRESH_CODES Set으로 분기 |
| PROJ_004 분기 | ⚠️ WARN | 메시지 일반화 — 액션 컨텍스트별 prefix 권장 |
| `ApiError` 변환 | ✅ PASS | client.ts 인터셉터에서 일관 |
| `isKnownTenantCode` 미사용 | ⚠️ WARN | dead export |

## 3. 권한 가드 / 라우팅

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| **ProjectSelect redirect 루프 위험** | ❌ **FAIL** | `ProjectSelect.tsx:33-41` setCurrent가 동일 값 재호출 → 구독자 불필요 재렌더. `if (!current \|\| current.projectId !== project.projectId)` 가드 필요 |
| SettingsLayout 동기화 | ⚠️ WARN | 이미 if 가드 존재, 안전하지만 deps에 current 포함 |
| RequireProject + ProjectSelect 동시 동작 | ✅ PASS | replace:true 사용으로 history 깨끗 |
| ApiKeysTab OWNER/ADMIN 가드 | ✅ PASS | 비관리자는 발급 버튼 미렌더 (구조적 차단) |
| MembersTab 본인행 일관성 | ✅ PASS | lockChange/lockDelete 동일 조건 |
| OWNER 행 잠금 사유 | ⚠️ WARN | 본인+OWNER 동시 케이스에 lock 아이콘만 — 사유 분기 필요 |
| ADMIN의 settings 진입 | ⚠️ WARN | RequireProject가 ADMIN을 `/projects`로 튕김 (수용 가능, 문서화 필요) |
| `role as` 캐스팅 | ⚠️ WARN | 2곳에서 runtime 보장 없는 cast — `narrowProjectRole` 헬퍼 권장 |

## 4. 보안

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| ApiKeyRevealModal 메모리 정리 | ⚠️ WARN | uncontrolled defaultValue + unmount value="". 그러나 부모 closure에 apiKey 잔존 가능 — `useState<string\|null>` 리팩터 권장 |
| **Esc/배경 클릭으로 키 영구 손실** | ❌ **FAIL** | ModalHost가 `dismissible:false` 미지원. 사용자 실수로 닫으면 평문 영구 손실 — Blocker |
| X-Member-Id 헤더 첨부 | ✅ PASS | client.ts 자동 첨부 |
| 키 응답 로깅 | ✅ PASS | mutation 결과를 캐시에 저장하지 않음 |
| 마스킹 placeholder | ✅ PASS | 정적 24개 점 |
| Clipboard fallback | ⚠️ WARN | execCommand deprecated (HTTPS 외 환경 fallback) |

## 5. UX / 접근성

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| 탭 키보드 (←→/Home/End) | ✅ PASS | roving focus 정상 |
| RoleSelect OWNER 미노출 | ✅ PASS | EDITOR/VIEWER 2개만 |
| OWNER 행 lock + 본인 안내 | ⚠️ WARN | 사유 tooltip 분기 필요 |
| 빈 멤버 상태 | ✅ PASS | "멤버가 없습니다" fallback |
| **ConfirmDialog message 미노출** | ❌ **FAIL** | ModalHost가 `desc/ok/cancel/danger` 읽으나, ui-bus.ts/호출부는 `message/confirmText/cancelText/tone` 사용. **API 키 발급/재발급/멤버 삭제 핵심 경고문 비가시화** — Sprint 1 회귀 표면화 |
| 마스킹 placeholder 이해 | ⚠️ WARN | "분실 시 재발급" 보조 텍스트 권장 |
| ApiKeyRevealModal autoFocus + select | ✅ PASS | requestAnimationFrame으로 정확 |
| 사업자번호 포맷 | ✅ PASS | 3-2-5 정확 |
| `🔒` 이모지 사용 | ⚠️ WARN | Icon.lock 미존재 — Icon.jsx 추가 후 교체 |
| shimmer keyframe 중복 | ⚠️ WARN | 3곳 인라인 중복 — global CSS로 통합 |

## 6. 회귀 / 빌드

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| npm run build | ✅ PASS | 217 modules 0 에러 |
| 미사용 import | ⚠️ WARN | tenant-errors.ts:isKnownTenantCode dead export |
| any 사용 | ✅ PASS | unknown 적절, as 1곳 |
| Sprint 1 회귀 | ⚠️ WARN | Icon.eye/eyeOff 유지. ConfirmDialog 결함이 Sprint 2 도메인에서 보안 위험으로 표면화 |

---

## Sprint 3 진입 전 반드시 해결할 Blocker

1. **ModalHost dismissible 옵션 + ConfirmDialog 키 일치 (Sprint 2.5 인프라 PR)**
   - ModalHost.jsx에서 `desc→message`, `cancel→cancelText`, `ok→confirmText`, `danger→tone==='danger'`로 필드명 정합화
   - `openModal(render, {dismissible})` 시그니처 추가, ApiKeyRevealModal에서 사용
   - ui-bus.ts의 `ModalRender` 시그니처 확장
2. **ProjectSelect setCurrent 무조건 호출 가드**
   - `ProjectSelect.tsx:33-41` SettingsLayout과 동일 가드 적용

## Sprint 2 follow-up (Sprint 3 첫 PR에 묶기)

- `api/types.ts:Role` deprecate, `schemas/tenant.ts:RoleType` 단일화
- `narrowProjectRole(role)` 헬퍼로 `as` 캐스팅 2곳 제거
- 본인 행 lock tooltip 분리
- `tenant-errors.ts:isKnownTenantCode` 사용처 추가 또는 제거
- `Icon.jsx`에 `lock` SVG 추가 → 🔒 이모지 교체
- shimmer keyframe global CSS 통합
- 키 미리보기 옆 "분실 시 재발급" 보조 안내
- API Key를 `OneTimePlaintextKey` branded type으로 모델링
- `revealKey`를 `useState<string\|null>` 기반으로 리팩터

## 다음 Sprint 권장 진행

Sprint 2의 **타입/도메인 모델링 품질은 Sprint 1보다 향상**(zod 분리, 에러 enum화, mutation invalidate 정합성). 다만 **Sprint 1 미해결 ModalHost 결함이 Sprint 2 보안 도메인(API 키)에 직격**한 점이 핵심 리스크. Modal/Confirm 인프라는 Segment Builder, Campaign Wizard 등 후속 모든 도메인의 기반이므로 **Sprint 3 첫 PR 또는 Sprint 2.5 인프라 PR로 ModalHost 정합성 작업 즉시 처리** 권장. 백엔드 `@PreAuthorize` 적용 일정과 FE 인라인 가드 제거 시점 동기화를 Sprint 3 시작 전 백엔드와 합의 필요.
