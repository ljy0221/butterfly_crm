# Sprint 2 — Tenant (Project/Member/API Key) UI 설계

> UI/UX Agent. Backend Liaison `docs/sprints/2-tenant/api.md` + Sprint 1 ui.md/review.md + 기존 공용 컴포넌트(`src/components/common`) + 기존 mock Settings(.jsx 5종) 기준.
>
> **작성**: 2026-05-01
> **전제**: 단일 projectId, X-Member-Id 임시 헤더, OWNER 액션 보호는 FE 가드(`RequireRole`)에서 담당.

---

## 1. 라우트 매핑

| 경로 | 페이지 | 가드 | 기존 라우트 상태 |
|---|---|---|---|
| `/projects` | `ProjectSelect` (자동 redirect/환영 fallback) | `RequireAuth` | placeholder → 교체 |
| `/p/:projectId/settings` | `SettingsLayout` (탭 컨테이너) | `RequireAuth` + `RequireProject` | placeholder → 교체 |
| `/p/:projectId/settings?tab=project` | `ProjectInfoTab` 마운트 | (인라인) | 신규 |
| `/p/:projectId/settings?tab=members` | `MembersTab` 마운트 | (인라인) | 신규 |
| `/p/:projectId/settings?tab=api-keys` | `ApiKeysTab` 마운트 | (인라인) | 신규 |

**탭 라우팅 결정**: nested route가 아닌 **search param `?tab=`**. (1) 탭마다 별도 레이아웃이 없고, (2) react-router 중첩 정의 부담 회피, (3) Sprint 1 admin 필터(`?status=`)와 일관, (4) 후속 FCM/감사 탭 추가 시 enum만 늘리면 됨. `useSearchParams()`로 읽고 기본값 `"project"`.

---

## 2. 페이지별 와이어프레임

### 2.1 ProjectSelect (`/projects`) — fallback 환영 화면

Sprint 1 follow-up #4에 따라 **로그인 직후 LoginPage가 직접 `/p/{id}/dashboard`로 이동**한다. `/projects`는 (a) URL 직접 진입, (b) `RequireProject`의 store 미초기화 fallback, (c) ADMIN의 우연 진입 시에만 노출.

```
+----------------------------------------+
|       [B] Butterfly                    |
|       반갑습니다, 홍길동 님            |
|  +----------------------------------+  |
|  | 버터플라이 마케팅팀              |  |
|  | OWNER · FREE 플랜                |  |
|  | [   대시보드로 이동   ]          |  |
|  +----------------------------------+  |
|       다른 계정으로 [로그아웃]         |
+----------------------------------------+
```

- **마운트 동작 시퀀스**:
  1. `useAuthStore.member` 확인. 없으면 `RequireAuth`가 이미 `/login`로 보냄.
  2. `member.projectId == null`(ADMIN) → "소속 프로젝트가 없습니다" 카드 + [관리자 페이지로] 버튼.
  3. `useProjectQuery(member.projectId)` 실행 → 성공 시 `useProjectStore.setCurrent({ projectId, name, role: member.role })` → `useEffect`로 `navigate(\`/p/${projectId}/dashboard\`, { replace: true })`.
  4. 즉시 redirect (fallback 화면이라 UX 거슬림 < 일관성).
- **에러 처리**: 403/404 → "프로젝트 접근 권한이 없습니다" + [로그아웃].
- **role 정보**: 단일 projectId 모델이라 `useAuthStore.member.role`이 곧 `currentProject.role`. 별도 self lookup 불필요.

---

### 2.2 SettingsLayout (`/p/:projectId/settings`) — 탭 컨테이너

```
+-----------------------------------------------------------+
|  PageHeader: 설정 / 프로젝트·멤버·API 키를 관리합니다.    |
+-----------------------------------------------------------+
|  [프로젝트 정보] [멤버] [API 키] | (FCM·감사: 후속)       |
|  -----------------------------------------------------    |
|  <ActiveTab content area>                                 |
+-----------------------------------------------------------+
```

- 탭바: 인라인 `<button role="tab">` + 활성 하단 `2px solid var(--color-primary-strong)`.
- 좌우 화살표 키 이동, Home/End 처음/끝.
- 상태: `const [tab, setTab] = useState(searchParams.get("tab") ?? "project")`. setTab 시 `setSearchParams({ tab })` 동기.
- OWNER 전용 탭(API 키)에 자물쇠 hint 미표시 — 클릭 가능하되 내부에서 분기.

#### 2.2.1 ProjectInfoTab — 프로젝트 정보 (VIEWER 이상 조회)

```
+-----------------------------------------------------------+
| Card: 프로젝트 정보                                       |
|   프로젝트 ID         #456                                |
|   프로젝트명          버터플라이 마케팅팀                 |
|   사업자번호          1234567890 (포맷 xxx-xx-xxxxx)      |
|   회사 인증번호       9876543210000                       |
|   플랜                FREE · 일 한도 1,000건              |
|   API 키 보유         [Tag tone=success] 발급됨           |
+-----------------------------------------------------------+
| Card: 소유자                                              |
|   [Avatar] 홍길동 / hong@example.com / memberId #123      |
+-----------------------------------------------------------+
```

- API: `GET /api/v1/projects/{projectId}` + `GET /members` (소유자 정보 추출)
- 편집 불가 (PUT 미제공) — `<dl>` KV 리스트
- 레이아웃: `grid-template-columns: 1fr 320px`

#### 2.2.2 MembersTab — 멤버 관리

```
+-----------------------------------------------------------+
| Card: 멤버 (3명)                                          |
|   ┌────────┬────────────┬───────────┬─────────┐           |
|   │ 이름   │ 이메일     │ 권한      │ 액션    │           |
|   ├────────┼────────────┼───────────┼─────────┤           |
|   │ 홍길동 │ hong@...   │ OWNER 🔒  │ —       │           |
|   │ 김철수 │ kim@...    │ [▾EDITOR] │ [🗑]    │           |
|   │ 이영희 │ lee@...    │ [▾VIEWER] │ [🗑]    │           |
|   └────────┴────────────┴───────────┴─────────┘           |
+-----------------------------------------------------------+
```

- API: `GET /api/v1/projects/{projectId}/members`
- 권한 셀(OWNER 화면): `<Select>` (VIEWER/EDITOR). OWNER 행 disabled + Tag warning + lock icon. 본인 행 disabled.
- 액션(OWNER 화면): 휴지통 — OWNER/본인은 "—"
- OWNER 외 사용자: 액션 컬럼 숨김, 권한 Tag만
- 삭제: confirmDialog → DELETE → invalidate
- 권한 변경: optimistic update
- 에러:
  - PROJ_007 → "OWNER는 삭제할 수 없습니다"
  - PROJ_008 → "OWNER 권한 이전은 별도 절차"
  - PROJ_004 → "권한이 없습니다"
  - PROJ_006 → invalidate + "이 멤버는 더 이상 프로젝트에 소속되지 않습니다"

#### 2.2.3 ApiKeysTab — API 키 (OWNER 전용 액션)

```
+-----------------------------------------------------------+
| Card: Public API 키                                       |
|   설명: 이벤트 수집·외부 연동에 사용. 발급 직후 1회만 평문 |
|   현재 상태  [Tag success dot] 발급됨                     |
|   키 미리보기  pk_live_•••••••••••••••••••••••• (정적)   |
|   [재발급]                                                |
+-----------------------------------------------------------+
미발급:
|   현재 상태  [Tag default] 미발급                         |
|   [발급]                                                  |
```

- 백엔드 GET 미제공 → 정적 `pk_live_••••••...`(24개 점) placeholder
- hasApiKey boolean으로 발급/재발급 모드 분기
- 발급/재발급: confirmDialog → POST → 평문 키 모달
- OWNER 외: "API 키 관리는 OWNER만" 안내

##### 2.2.3.1 ApiKeyRevealModal — 1회 평문 노출

```
+----------------------------------------+
|  API 키가 발급되었습니다  [×]          |
|  ⚠️ 이 화면을 닫으면 다시 볼 수 없습니다 |
|  ┌──────────────────────────────┬───┐  |
|  │ pk_live_a1b2c3d4...          │📋 │  |
|  └──────────────────────────────┴───┘  |
|  [복사하고 닫기]                       |
+----------------------------------------+
```

- `dismissible: false` — Esc/배경 클릭 차단 (ModalHost에 옵션 추가 필요)
- 키 영역: monospace 14px, dark 배경, autoFocus + select()
- 복사: `navigator.clipboard.writeText` + toast
- 모달 unmount 시 키 메모리에서 즉시 제거

---

## 3. 상태 / 로딩 / 에러

| 상황 | 처리 |
|---|---|
| 프로젝트 조회 로딩 | KV skeleton 6행 + 소유자 카드 skeleton |
| 멤버 목록 로딩 | 5행 skeleton |
| 권한 변경 중 | row optimistic, select 잠시 disabled |
| 멤버 삭제 중 | row opacity 0.5 |
| API 키 발급/재발급 중 | 버튼 disabled "발급 중..." |
| 403 / 권한 변동 | invalidate + 안내 토스트 |
| 모달 열린 상태 새로고침 | 키 소실 — 재발급 안내 |

---

## 4. 권한별 UI 매트릭스

| 액션 | VIEWER | EDITOR | OWNER | ADMIN |
|---|:---:|:---:|:---:|:---:|
| 프로젝트 정보 조회 | ✅ | ✅ | ✅ | ✅ |
| 멤버 목록 조회 | ✅ | ✅ | ✅ | ✅ |
| 멤버 권한 변경 | Tag only | Tag only | ✅ | ✅ |
| 멤버 삭제 | 컬럼숨김 | 컬럼숨김 | ✅ | ✅ |
| API 키 발급/재발급 | 안내문 | 안내문 | ✅ | ✅ |
| OWNER 행 액션 | n/a | n/a | disabled+lock | disabled+lock |
| 본인 행 삭제 | n/a | n/a | disabled | disabled |

`RequireRole role="OWNER"`는 **탭 단위가 아닌 액션 단위**로 인라인 분기. SettingsLayout 자체에는 RequireRole 미적용.

---

## 5. 접근성

- 탭 ←/→/Home/End 키보드 이동, `role="tablist/tab"`
- 휴지통 `aria-label="멤버 삭제: {name}"`
- 키 모달 `aria-modal="true"`, focus trap (ModalHost 가정)
- 키 input `autoFocus` + onFocus select() — Cmd/Ctrl+C 즉시
- 비활성 액션 `title` + `aria-disabled="true"`

---

## 6. Frontend Agent 노트

### 6.1 신규 / 변경 파일

```
src/
├─ api/tenant.ts                          (신규)
├─ schemas/tenant.ts                      (신규)
├─ hooks/queries/tenant.ts                (신규)
├─ lib/tenant-errors.ts                   (신규: PROJ_002~008)
├─ pages/ProjectSelect.tsx                (교체)
├─ features/settings/
│  ├─ SettingsLayout.tsx                  (신규)
│  ├─ ApiKeyRevealModal.tsx               (신규)
│  └─ tabs/
│     ├─ ProjectInfoTab.tsx               (신규)
│     ├─ MembersTab.tsx                   (신규)
│     └─ ApiKeysTab.tsx                   (신규)
└─ router/index.tsx                       (수정)
```

기존 `Settings.jsx` 등 mock은 **삭제하지 않고 그대로 둠**(다른 진입로 보존).

### 6.2 핵심 결정

1. 탭은 search param `?tab=` (Sprint 1 admin 일관성)
2. role은 `useAuthStore.member.role`에서 (단일 projectId 모델)
3. OWNER 외 가드는 인라인 if (탭 wrap 금지)
4. API 키 모달 `dismissible: false` — ModalHost 옵션 추가 필요
5. 마스킹은 정적 placeholder
6. 에러 매핑은 `lib/tenant-errors.ts` 분리
7. ProjectStore.setCurrent 시점: ProjectSelect 또는 useProjectQuery 성공 시

### 6.3 신규 컴포넌트 최소화

Tabs / KVList 등 일반 컴포넌트 만들지 않음. SettingsLayout 인라인 + Card 조합.
**ApiKeyRevealModal**만 도메인 경고 UX 강도 때문에 별도 파일.

### 6.4 후속 Sprint 예약

- FCM Private Key 탭: SettingsLayout 4번째 탭 placeholder
- 감사 탭: 동일 패턴
- 백엔드 `@PreAuthorize` 적용 후 → 인라인 if 가드 제거, 백엔드 403 신호로 단일화

---

**버전**: 1.0
**다음 단계**: Frontend Agent 구현 → CTO 검수
