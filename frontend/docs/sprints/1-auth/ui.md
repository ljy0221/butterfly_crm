# Sprint 1 — Auth + Application UI 설계

> UI/UX Agent 작성. Backend Liaison의 `api.md` 계약서 + `tokens.css` 디자인 토큰 + 기존 공용 컴포넌트(`src/components/common`)를 기준으로 작성.
>
> **작성**: 2026-05-01
> **컴포넌트 라이브러리**: Button / Input / Card / Checkbox / Select / Tag / PageHeader (있는 것만 재사용, 신규 컴포넌트 최소화)

---

## 1. 라우트 매핑

| 경로 | 페이지 | 가드 | 기존 라우트 상태 |
|---|---|---|---|
| `/login` | `LoginPage` | AuthLayout 하위 (공개) | placeholder → 교체 |
| `/signup` | `SignupPage` (4-step state 위저드, 단일 라우트) | AuthLayout (공개) | placeholder → 교체 |
| `/apply` | `ApplyPage` (신청서 공개 폼) | AuthLayout (공개) | placeholder → 교체 |
| `/owner/setup?token=XXX` | `OwnerPasswordSetup` | AuthLayout (공개, 쿼리 토큰만) | placeholder → 교체 |
| `/admin/applications` | `AdminApplications` | `RequireAuth` + `RequireAdmin` + `AppLayout` | placeholder → 교체 |

> `router/index.tsx`의 element만 교체. 라우트 트리 자체는 변경 없음.

---

## 2. 페이지별 와이어프레임

### 2.1 LoginPage (`/login`)

```
            +----------------------------------------+
            |    [B]  Butterfly                       |
            |    CRM Operator Console (caption)       |
            |                                         |
            |  +-----------------------------------+  |
            |  |  로그인                          |  |
            |  |  이메일                           |  |
            |  |  [______________________________] |  |
            |  |  비밀번호                         |  |
            |  |  [______________________] [눈]   |  |
            |  |  [   로그인   ]   <- fullWidth   |  |
            |  |  [에러 배너 (조건부)]             |  |
            |  +-----------------------------------+  |
            |  계정이 없나요?  회원가입               |
            |  서비스 신청은  여기                    |
            +----------------------------------------+
```

- **레이아웃**: `AuthLayout`이 이미 gradient 배경 + center 정렬 처리. 카드 폭 `420px`.
- **사용 컴포넌트**: `Card`(title="로그인"), `Input`, `Button`(variant=`primaryStrong`, size=`lg`, style fullWidth).
- **비밀번호 토글**: 신규 컴포넌트 X — Input을 `<div style={{ position: "relative" }}>`로 감싸 `<button aria-label="비밀번호 표시">{Icon.eye}</button>` 추가.
- **링크**: `<Link to="/signup">` `<Link to="/apply">` — `var(--fg-link)` 컬러.
- **폼 검증**: email z.email, password z.string().min(1) (백엔드 검증에 위임).
- **제출 플로우**:
  1. `POST /api/v1/auth/login`
  2. 성공 → `useAuthStore.setAuth({ accessToken: "", refreshToken: "", member: { memberId, email, name, role: roleType } })`
  3. ADMIN이면 `/admin/applications`, 그 외엔 `/projects`
- **에러 처리** (ApiError.code 분기):
  - `AUTH_006` → 카드 하단 빨간 배너 "이메일 또는 비밀번호가 올바르지 않습니다"
  - `AUTH_016` (PASSWORD_NOT_SET) → `toast("신청서 승인 후 받은 이메일에서 비밀번호를 먼저 설정해주세요", "warning")`
  - 그 외 → 토스트 fallback

---

### 2.2 SignupPage (`/signup`) — 4-step state 위저드

```
+----------------------------------------+
|  [<- 뒤로]  회원가입                    |
|  서비스를 이용하려면 회사 인증번호이...   |
|  ●——●——○——○    1/4 이메일 인증          |
+----------------------------------------+
|  [step별 카드 본문]                     |
|  [ 이전 ]                  [ 다음 ]    |
+----------------------------------------+
```

#### Step 1 — 이메일 인증
- 인풋 우측에 `[코드받기]` 버튼 → 발송 후 `[재전송 (NNs)]` (60초 카운트다운)
- 발송 후 6자리 코드 입력란 + `[인증하기]` 버튼 등장
- 인증 성공 시 인풋 readonly + `Tag tone="success"` "인증 완료"
- API: `POST /api/v1/auth/email/send-code`, `POST /api/v1/auth/email/verify`
- 에러: AUTH_001(중복 이메일) / AUTH_005(쿨다운) / AUTH_003(만료) / AUTH_004(불일치)
- "다음"은 인증 완료 시에만 활성

#### Step 2 — 회사 인증번호
- 단일 인풋. 클라 검증은 not-empty만. 실제 검증은 step4 signup 시점 백엔드 `AUTH_015` → step2로 jump

#### Step 3 — 개인정보
- grid 2열: 이름 / 회사명 / 전화번호 / 생년월일
- 회사명은 백엔드 페이로드에 들어가지 않으므로 FE 보관용 (TODO: 기획 확인)
- 검증: nameSchema(20자) / companySchema(30자) / phoneSchema(11자리) / birthDateSchema(YYYY-MM-DD)

#### Step 4 — 비밀번호
- 비밀번호 + 비밀번호 확인 + 약관 동의 체크박스
- 검증: passwordSchema, confirm 일치, agreedToTerms === true
- 제출 → `POST /api/v1/auth/signup`
- 성공 → 토스트 + `/login` redirect (이메일 prefill)
- AUTH_002 → step1 jump / AUTH_015 → step2 jump / AUTH_001 → step1 jump

---

### 2.3 ApplyPage (`/apply`) — 비로그인 공개 신청서

```
+--------------------------------------------------+
|  Butterfly                            [로그인]    |
|  서비스 신청서                                   |
|  +--------------------------------------------+  |
|  |  신청자 정보 (이름/이메일/전화/생년월일)   |  |
|  |  사업자 정보 (사업자명/사업자번호)         |  |
|  |  [ ] 개인정보 처리방침에 동의합니다       |  |
|  |              [ 신청하기 ]                  |  |
|  +--------------------------------------------+  |
+--------------------------------------------------+
```

- 카드 폭 580px
- 검증: applicantName(20) / email / phone(11) / birthDate / businessName(30) / businessNo(10자리 숫자)
- 제출 → `POST /api/v1/applications`
- 성공 시 카드 자리에 success view 교체 (#101 안내 + [로그인으로 이동])
- APPLY_002(중복 사업자번호) → businessNo 인풋 error

---

### 2.4 OwnerPasswordSetup (`/owner/setup?token=XXX`)

- 마운트 시 `useSearchParams().get("token")` 추출. 없으면 즉시 `/login` redirect + 토스트
- 비밀번호 + 비밀번호 확인 (passwordSchema + 일치)
- 제출 → `POST /api/v1/auth/owner/set-password` { token, password }
- 성공 → 토스트 + `/login` redirect
- AUTH_017(링크 만료) / AUTH_008(회원 미존재) → 인풋 비활성 + 카드 하단 안내

---

### 2.5 AdminApplications (`/admin/applications`) — ADMIN 전용

```
+-------------------------------------------------------------+
|  PageHeader: 신청서 승인                                    |
|  [전체|대기중|승인|반려]  검색: [__________]                |
|  +-------------------------------------------------------+  |
|  | 신청자 | 사업자명 | 사업자번호 | 상태 | 신청일 |›       |  |
|  +-------------------------------------------------------+  |
|              [<] 1 2 3 [>]                                  |
+-------------------------------------------------------------+
```

- 필터 탭 4개 — `status` 쿼리(`PENDING|APPROVED|REJECTED|undefined`)
- 행 클릭 → 상세 Modal (UIBus.openModal):
  - 신청자/사업자 정보 + 반려 사유 textarea + [반려]/[승인] 버튼
  - 승인 → confirmDialog → `POST /admin/applications/{id}/approve` → 목록 invalidate
  - 반려 시 reason 빈 값이면 인풋 error
- 빈 상태: "📭 승인 대기 중인 신청서가 없습니다"
- 로딩: 6개 skeleton row (inline div)
- 페이지네이션 하단 우측 정렬 (Button variant="ghost" size="sm")

---

## 3. 폼 검증 룰 (Zod)

```ts
// src/schemas/auth.ts
export const emailSchema = z.string().min(1, "이메일을 입력하세요").email("올바른 이메일 형식이 아닙니다");
export const passwordSchema = z.string()
  .min(8, "8자 이상 입력해주세요").max(20, "20자 이내")
  .regex(/[a-z]/, "영소문자를 포함해주세요")
  .regex(/[0-9]/, "숫자를 포함해주세요");
export const phoneSchema = z.string().regex(/^\d{11}$/, "11자리 숫자(-제외)로 입력해주세요");
export const nameSchema = z.string().min(1, "이름을 입력하세요").max(20, "20자 이내");
export const companySchema = z.string().min(1, "회사명을 입력하세요").max(30, "30자 이내");
export const birthDateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "YYYY-MM-DD 형식");
export const emailVerifyCodeSchema = z.string().length(6, "6자리 코드를 입력해주세요");
export const companyAuthNoSchema = z.string().min(1, "회사 인증번호를 입력하세요");

export const loginSchema = z.object({ email: emailSchema, password: z.string().min(1) });
export const signupSchema = z.object({
  email: emailSchema, password: passwordSchema, passwordConfirm: passwordSchema,
  name: nameSchema, company: companySchema, phone: phoneSchema, birthDate: birthDateSchema,
  companyAuthNo: companyAuthNoSchema,
  agreedToTerms: z.literal(true, { errorMap: () => ({ message: "약관에 동의해주세요" }) }),
}).refine((d) => d.password === d.passwordConfirm, { message: "비밀번호가 일치하지 않습니다", path: ["passwordConfirm"] });

// src/schemas/application.ts
export const businessNoSchema = z.string().regex(/^\d{10}$/, "10자리 사업자번호를 입력해주세요");
export const applicationSchema = z.object({
  applicantName: nameSchema, email: emailSchema, phone: phoneSchema, birthDate: birthDateSchema,
  businessName: z.string().min(1).max(30), businessNo: businessNoSchema,
  agreed: z.literal(true, { errorMap: () => ({ message: "개인정보 처리방침에 동의해주세요" }) }),
});
```

> **TODO 기획 확인**:
> - 사업자번호가 10자리/13자리 중 어느 것인지 (현재 api.md 예시는 10자리 → 따름)
> - 회사명이 백엔드 signup payload에 미포함 → 회사 인증번호 검증 시 자동 매핑되는지

---

## 4. 권한별 UI 차이

| 페이지 | 비로그인 | VIEWER/EDITOR/OWNER | ADMIN |
|---|---|---|---|
| `/login` | 노출 | redirect → `/projects` | redirect → `/admin/applications` |
| `/signup` | 노출 | redirect → `/projects` + 토스트 | 동일 |
| `/apply` | 노출 ([로그인]버튼) | 노출 | 노출 |
| `/owner/setup` | 토큰만 있으면 | 노출 | 동일 |
| `/admin/applications` | RequireAuth → `/login` | RequireAdmin 안내 | 정상 노출 |

- `/login`/`/signup` 진입 시 `useAuthStore.isAuthenticated()` true면 useEffect로 redirect

---

## 5. 빈 / 로딩 / 에러 상태

| 상황 | 처리 |
|---|---|
| 신청서 목록 빈 | "📭 승인 대기 중인 신청서가 없습니다" |
| 신청서 목록 로딩 | 6개 skeleton row |
| 코드 발송 중 | `[발송 중...]` disabled |
| 코드 발송 후 60초 | `[재전송 (NNs)]` setInterval |
| 회원가입 제출 중 | step4 버튼 `[가입 중...]` disabled |
| 신청 제출 후 | 카드를 success view로 교체 |
| 네트워크 에러 | `toast("일시적인 오류가 발생했습니다", "error")` |
| 401 인터셉트 | client.ts가 refresh → 실패 시 store.clear + `/login` redirect |

---

## 6. 접근성 / 키보드 / 포커스

- **자동 포커스**: 위저드 단계 전환 시 첫 인풋 focus (`useEffect` + ref)
- **Esc**: ModalHost가 처리
- **Enter 제출**: `<form onSubmit>`로 자동
- **disabled 시각**: Button 호출 측 style override (`opacity:.5, cursor:"not-allowed"`)
- **aria-label**: 비밀번호 토글 / 모달 닫기 / 위저드 진행 dots
- **에러 메시지**: Input의 `error` prop, 제출 실패 시 첫 에러 인풋으로 scrollIntoView

---

## 7. 변경 없음 (기존 .jsx 그대로 마운트 가능한 화면)

- 없음. Sprint 1은 신규 인증 도메인이라 모든 페이지가 신규 구현.
- AuthLayout/AppLayout/ToastHost/ModalHost/공용 컴포넌트는 그대로 재사용.

---

## 8. Frontend Agent를 위한 노트

### 8.1 신규 폴더/파일 구조

```
src/
├─ features/
│  ├─ auth/
│  │  ├─ LoginPage.tsx
│  │  ├─ SignupPage.tsx              (4-step state 위저드)
│  │  ├─ steps/
│  │  │  ├─ EmailVerifyStep.tsx
│  │  │  ├─ CompanyAuthStep.tsx
│  │  │  ├─ PersonalInfoStep.tsx
│  │  │  └─ PasswordStep.tsx
│  │  └─ OwnerPasswordSetup.tsx
│  ├─ application/
│  │  └─ ApplyPage.tsx
│  └─ admin/
│     ├─ AdminApplications.tsx
│     └─ ApplicationDetailModal.tsx
├─ schemas/
│  ├─ auth.ts
│  └─ application.ts
├─ api/
│  ├─ auth.ts
│  └─ application.ts
├─ hooks/queries/
│  ├─ auth.ts                         (useLoginMutation, useSendCodeMutation, useVerifyCodeMutation, useSignupMutation, useOwnerSetPasswordMutation)
│  └─ application.ts                  (useCreateApplicationMutation, useApplicationsQuery, useApproveMutation, useRejectMutation)
└─ lib/
   └─ auth-errors.ts                  (mapAuthError 헬퍼)
```

### 8.2 핵심 결정

1. **회원가입 = 단일 라우트 + step state**. 새로고침 시 진행 휘발 OK.
2. **JWT 미구현 대응**: store에 `accessToken=""` 저장, `member.memberId`만 의존. `client.ts`가 빈 토큰 시 X-Member-Id만 첨부.
3. **isAuthenticated()는 `!!get().member`로 변경 필요** (현재 accessToken 길이를 봄). store/auth.ts 수정 항목.
4. **member에 role 필드 추가**: 로그인 응답의 `roleType`을 store에 저장 → ADMIN 라우팅에 활용.
5. **에러 코드 → UI 분기 헬퍼**: `lib/auth-errors.ts`에 `mapAuthError(err)` 둬 페이지마다 재사용.

### 8.3 디자인 토큰 사용 예

```tsx
<Card style={{ width: 420, padding: "var(--space-6)" }} title="로그인">
  <Input label="이메일" ... />
  <div style={{ height: "var(--space-3)" }} />
  <Input label="비밀번호" ... />
  <div style={{ height: "var(--space-5)" }} />
  <Button variant="primaryStrong" size="lg" style={{ width: "100%" }}>로그인</Button>
</Card>
```

### 8.4 사이드바 nav 추가 (TODO)

ADMIN role일 때만 "신청서 승인" 항목을 사이드바에 노출. `nav-mapping.ts`/Sidebar 상태 확인 후 구현.

---

**버전**: 1.0
**다음 단계**: Frontend Agent 구현 → CTO 검수
