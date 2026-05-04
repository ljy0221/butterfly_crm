# Sprint 1 — Auth + Application CTO 검수 보고서

**작성일**: 2026-05-01
**검수 대상**: Sprint 1 산출물 (auth + application 도메인)
**빌드 상태**: ✅ npm run build 209 modules 0 에러

## Executive Summary

Sprint 1 산출물은 백엔드 DTO와 1:1 정합성이 유지되어 있고, ApiResponse unwrap·Page 응답 처리·X-Member-Id 임시 헤더 정책이 client.ts에 일관되게 구현되어 있어 **Sprint 2 진입 가능 상태**다. 다만 (1) 비밀번호 토글 아이콘으로 의미 부적합한 `Icon.search`를 LoginPage·PasswordStep에서 사용하는 점, (2) AdminApplications 진입 시 ADMIN이 아닌 사용자가 빈 메시지 화면만 보고 빠져나갈 수단이 없는 점, (3) Signup 위저드의 `company` 필드가 백엔드로 전송되지 않은 채 수집만 되는 점은 머지 직후 후속 PR로 정리한다. **Blocker 없음**.

---

## 1. API/DTO 정합성

| 항목 | 등급 | 발견 사항 | 수정 권장 |
|---|---|---|---|
| LoginRequest (email, password) | ✅ PASS | `src/api/auth.ts:59-62` ↔ `LoginRequest.java:6-12` 정확 일치 | — |
| SignupRequest 7필드 | ✅ PASS | email/password/name/phone/birthDate/companyAuthNo/agreedToTerms 모두 camelCase 일치 (`auth.ts:49-57`) | — |
| LoginResponse / MemberInfoResponse | ✅ PASS | `accessToken/refreshToken/member`, `memberId/email/name/phone/roleType/projectId/createdAt` 모두 일치 | — |
| ApplicationCreateRequest | ✅ PASS | applicantName/email/phone/birthDate/businessName/businessNo 정확 (`application.ts:14-21`) | — |
| ApplicationResponse | ✅ PASS | rejectReason/reviewedAt nullable 처리, status enum 동일 (`application.ts:27-39`) | — |
| Spring Page 응답 | ✅ PASS | `SpringPage<T>`(`application.ts:45-54`)가 PageImpl 직렬화 키와 일치 | — |
| ApiResponse 래퍼 unwrap | ✅ PASS | `client.ts:51-74`에서 자동 unwrap, 모든 도메인 함수에서 `res.data` 일관 사용 | — |
| signup payload `company` 필드 | ⚠️ WARN | Step3에서 수집(`PersonalInfoStep.tsx:48-57`)하지만 백엔드 미전송(`SignupPage.tsx:117-125`). 백엔드 SignupRequest에도 필드 없음 | 수집 제거하거나 백엔드에 필드 추가 |
| RoleType 일관성 | ✅ PASS | FE `"ADMIN"|"OWNER"|"EDITOR"|"VIEWER"`가 백엔드 enum과 일치 | — |

## 2. 에러 코드 매핑

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| 백엔드 enum 실재 코드 | ℹ️ INFO | AuthErrorCode: AUTH_001~AUTH_017(7,8 일부 결번)+PROJ_001+SYS_001. TenantErrorCode: PROJ_002~PROJ_008, APPLY_001~APPLY_005. CommonErrorCode: COMMON_001~005, COMMON_999 |
| `lib/auth-errors.ts` 코드 커버리지 | ✅ PASS | DEFAULT_MESSAGES에 백엔드 enum 모든 코드 포함 |
| AUTH_006 (자격증명) 분기 | ✅ PASS | LoginPage가 인라인 배너 / AUTH_016은 warning 토스트로 분리 |
| AUTH_016 (PASSWORD_NOT_SET) 메시지 | ✅ PASS | "신청서 승인 후 받은 이메일에서 비밀번호를 먼저 설정해주세요" |
| AUTH_017 (오너 링크 만료) | ✅ PASS | OwnerPasswordSetup에서 `AUTH_017 \|\| AUTH_008` 만료 화면으로 전환 |
| step jump 매핑 (AUTH_001/002/015) | ✅ PASS | SIGNUP_JUMP가 1(이메일)·2(회사인증) 정확 |
| APPLY_002 / APPLY_005 | ✅ PASS | businessNo / rejectReason 필드에 매핑. ApplyPage에서 APPLY_002 직접 분기 추가됨 |

## 3. 권한 가드 / 라우팅

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| `/admin/applications` 가드 | ✅ PASS | RequireAuth → RequireAdmin 이중 래핑 |
| ADMIN 자동 redirect | ✅ PASS | LoginPage가 `member.role === "ADMIN"`일 때 `/admin/applications`. 가드와 충돌 없음 |
| 비ADMIN이 `/admin` 직접 진입 | ⚠️ WARN | `guards.tsx:55-61`에서 정적 메시지만 렌더, 빠져나갈 링크 없음 |
| `/login`·`/signup` 자동 redirect 루프 | ✅ PASS | useEffect가 한 번만 navigate, SignupPage는 인증된 사용자 진입 시 redirect 없음(루프 위험 X) |
| isAuthenticated 일관성 | ✅ PASS | store/guards/페이지 모두 `!!member` 기준 — 빈 토큰 OK |
| ADMIN의 `/projects` 직접 진입 | ℹ️ INFO | RequireAuth만 통과해 ProjectSelect 렌더. 큰 문제 없으나 일관성 측면 추후 정리 |
| `member.projectId` 직접 redirect 미적용 | ❌ FAIL | LoginPage가 OWNER/EDITOR/VIEWER 모두 `/projects`로 보냄. 단일 프로젝트라면 `/p/{id}/dashboard` 직행이 자연스러움 |

## 4. 보안

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| 토큰 저장 위치 | ✅ PASS(현 시점) | sessionStorage. JWT 미구현이라 빈 문자열 — 추후 활성화 시 refresh를 httpOnly 쿠키로 이전 |
| 비밀번호 input type | ✅ PASS | type 토글, autoComplete `current-password`/`new-password` 적절 |
| 공개 폼 X-Member-Id 미부착 | ✅ PASS | `client.ts:41-44` member null 시 헤더 미첨부 |
| Authorization 빈 토큰 가드 | ✅ PASS | `client.ts:36-39` length>0 조건 |
| rejectReason 길이 제한 | ⚠️ WARN | 백엔드 `@Size(max=500)` vs textarea maxLength 미설정. `maxLength={500}` 권장 |
| /owner/setup 토큰 노출 | ℹ️ INFO | URL referer leak 가능하나 1회용·만료 보호 |
| 401 refresh 무한루프 방지 | ✅ PASS | `_retried` 플래그 + auth.ts가 별도 fetch 사용 |

## 5. UX / 접근성

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| EmailVerifyStep 쿨다운 cleanup | ✅ PASS | `clearInterval` 정상 |
| 폼 필드 에러 자동 클리어 | ✅ PASS | ApplyPage update(), CompanyAuthStep, ApplicationDetailModal 모두 |
| 인라인 vs 토스트 일관성 | ✅ PASS | 자격증명=배너, step jump=필드, 그 외=토스트 |
| 위저드 step autoFocus | ✅ PASS | 모든 step 첫 인풋 autoFocus |
| 비밀번호 토글 aria-label | ✅ PASS | 모든 토글 버튼 적용 |
| **비밀번호 토글 아이콘 의미** | ❌ FAIL | `LoginPage.tsx:188`·`PasswordStep.tsx:48,73`이 `Icon.search`(돋보기)를 사용. `Icon.jsx`에 eye 미정의. **수정 필수** |
| ApplicationDetailModal Esc | ✅ PASS | ModalHost가 처리 |
| AdminApplications 상태 분기 | ✅ PASS | 로딩 skeleton·에러 행·빈 상태 모두 구현 |
| 페이지네이션 | ✅ PASS | totalPages>1일 때만 표시, 경계값 disable |
| 행 키보드 접근성 | ⚠️ WARN | `<tr onClick>`만 있고 role/tabIndex/onKeyDown 없음 |
| RequireAdmin 거부 화면 | ⚠️ WARN | 액션 버튼 부재 (#3 중복 지적) |

## 6. 회귀 / 빌드

| 항목 | 등급 | 발견 사항 |
|---|---|---|
| npm run build | ✅ PASS | 209 modules 0 에러 |
| 미사용 import | ✅ PASS | 도메인 코드 모두 사용됨 |
| any 사용처 | ✅ PASS | ambient `index.d.ts`(의도된 호환 레이어) 외 없음 |
| placeholder 라우트 충돌 | ✅ PASS | 분리된 path tree |
| ui-bus.js / ui-bus.ts 공존 | ℹ️ INFO | .ts 우선이지만 단일화 권장 |

---

## Sprint 2 진입 전 반드시 해결할 Blocker

- **없음**. Sprint 2 즉시 진입 가능.

## Sprint 1 follow-up (Sprint 2 첫 PR에 묶어 처리)

1. ❌ **비밀번호 토글 아이콘 정상화** — `Icon.jsx`에 `eye`/`eyeOff` 추가, `LoginPage.tsx:188`·`PasswordStep.tsx:48,73`·`OwnerPasswordSetup.tsx:122,148`에서 교체
2. ⚠️ **Signup `company` 필드 처리 결정** — 백엔드 SignupRequest에 추가하거나 위저드에서 제거
3. ⚠️ **rejectReason maxLength={500}** — `ApplicationDetailModal.tsx:145-163`
4. ❌ **단일 프로젝트 OWNER 즉시 dashboard 이동** — `LoginPage.tsx:70` 분기 추가
5. ⚠️ **RequireAdmin 거부 화면에 액션 버튼** — `guards.tsx:55-61`
6. ⚠️ **AdminApplications 행 키보드 접근** — `<tr>` tabIndex/onKeyDown
7. ℹ️ **ui-bus.js / ui-bus.ts 단일화**
8. ℹ️ **JWT 활성화 후 후속 작업** — refresh를 httpOnly 쿠키로 이전, X-Member-Id 헤더 제거

## 다음 Sprint 권장 진행

Sprint 2(테넌트/프로젝트 도메인)에 즉시 자원 투입. 다만 첫 PR로 위 follow-up 7건 묶기. 비밀번호 토글 아이콘은 사용자 즉시 인지 결함이라 데모 전 정리. Sprint 2 진입과 함께 Backend Liaison에게 (a) JWT 활성화 ETA, (b) SignupRequest 회사명 필드 추가 여부, (c) `/admin/applications` `@PreAuthorize` 적용 시점을 확인.
