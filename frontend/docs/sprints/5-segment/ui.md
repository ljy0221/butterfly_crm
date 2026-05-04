# Sprint 5 — Segment UI 설계

> UI/UX Agent. Backend Liaison `docs/sprints/5-segment/api.md` + Sprint 1~4 ui.md/review.md 패턴 + 기존 mock(`Segments.jsx`, `SegmentBuilder.jsx`) + 공용 컴포넌트(`src/components/common`) + mock 인프라(`src/api/_mock/index.ts`) 기준.
>
> **작성**: 2026-05-01
> **전제**: 8개 실 API + 3개 mock(`segment_users`, `segment_rebuild`, `segment_linked_campaigns`). VIEWER↑ 조회/즐겨찾기, EDITOR↑ CRUD/preview/rebuild. 조건 트리는 sealed interface 5종(EventNode/UserNode/TagNode/DeviceNode/GroupNode) + Period 3종 + Operator 8종 polymorphic 직렬화. 권한 가드 인라인.

---

## 1. 라우트 매핑

| 경로 | 페이지 | 가드 |
|---|---|---|
| `/p/:projectId/segments` | `Segments.tsx` | RequireAuth + RequireProject + AppLayout |
| `/p/:projectId/segments/new` | `SegmentBuilder.tsx` (mode="create") | EDITOR↑ (VIEWER → 목록 redirect) |
| `/p/:projectId/segments/:id` | `SegmentBuilder.tsx` (mode="view"\|"edit") | VIEWER↑ 진입, EDITOR↑ 수정 토글 |

빌더는 페이지 한 화면 점유. 생성/상세/수정을 단일 컴포넌트로 합치고 mode prop + 내부 토글.

---

## 2. 와이어프레임

### 2.1 Segments 목록
```
+----------------------------------------------------------------+
| PageHeader: 세그먼트 / [+ 새 세그먼트] (EDITOR↑)                |
+----------------------------------------------------------------+
| ⭐ 즐겨찾기 (가로 칩 줄)                                        |
| 🕒 최근 사용 (가로 칩 줄)                                       |
+----------------------------------------------------------------+
| [SearchInput] [생성자 ▾] [생성일 ▾] [정렬 ▾]                   |
+----------------------------------------------------------------+
| 테이블: ⭐ | 이름/설명 | 생성자 | 예상모수 | 캠페인수 | 빌드상태 |
+----------------------------------------------------------------+
| 페이지네이션                                                    |
+----------------------------------------------------------------+
```

행 구성:
- ⭐ 토글 (낙관적 업데이트, 실패 시 롤백)
- 이름 + 설명 2줄
- 생성자 Avatar + 이름
- 예상 모수 fmtNum (PENDING="—", BUILDING=spinner, FAILED=빨간 배지)
- linkedCampaignCount Tag
- BuildStatus 배지 (READY=success, PENDING=warning, BUILDING=info-pulse, FAILED=danger)
- ⋯ 메뉴(EDITOR↑): 수정/복제(P1)/삭제

### 2.2 SegmentBuilder

핵심 화면. 좌(2fr) 빌더 + 우(1fr sticky) 미리보기 패널.

```
+---------------------------------------------------------------+
| PageHeader: [< 세그먼트] · 빌더 / [수정] or [취소][저장]       |
+---------------------------------------------------------------+
| 좌측 (2fr)                       | 우측 (1fr, sticky top)     |
|  기본 정보 (이름/설명)            | 예상 모수 카드             |
|  조건 트리                       | [미리보기] / 갱신시각       |
|    AND/OR 그룹                   | 빌드 상태 카드             |
|    ├ EventNode ⋯                 | [재계산](mock)             |
|    ├ UserNode ⋯                  | 매칭 사용자(Phase 8) ❌    |
|    └ [+ 조건 추가]               | 연결 캠페인(Phase 7) ❌    |
+---------------------------------------------------------------+
```

#### 조건 트리 노드 5종

- **GroupNode**: AND/OR 토글 + `[+ 조건 추가]` + `[+ 그룹]` + ⋯(삭제, 최상위 불가). conditions 배열 재귀 렌더
- **EventNode**: category Select / filters[] (key/op/value 행) / period (Relative=일수, Absolute=시작·종료, Cumulative=고정) / minCount
- **UserNode**: key Select(birthDate/joinedAt) + op Select(8종) + value (key별 input 분기)
- **TagNode**: key text + op Select(eq/neq/in/contains) + value (in이면 콤마 split)
- **DeviceNode**: key Select(os/deviceType) + op + value Select

조건 추가 UX: `[+ 조건 추가]` → 4개 칩 드롭다운(이벤트/사용자/태그/디바이스) → 선택 시 leaf form 펼침
노드별 ⋯ 메뉴: [그룹으로 감싸기] / [복제] / [삭제]

시각: 그룹 박스 좌측 logic 배지(AND=info, OR=warning), 자식 사이 `── AND ──` 디바이더, 깊이별 들여쓰기 16px

#### 미리보기 패널
- 초기: "미리보기를 실행해 모수를 확인하세요" + [미리보기]
- 클릭 → POST /segments/preview (현재 conditionTree 직렬화)
- 응답: estimatedCount, method(HLL/EXACT), estimatedAt
- 표시: 큰 숫자 + ⓘ HLL 추정치 + "5분 전"
- **저장 버튼 활성 조건**: previewCallCount >= 1
- 트리 변경 시 dirty=true → 저장 옆에 "조건 변경됨, 미리보기 재실행 권장" 경고
- 에러: SEGMENT_501_EVENT_NOT_READY → "이벤트 모듈 합류 전까지 EventNode 미리보기 불가"

#### view ↔ edit 토글
- view 모드: 모든 폼 필드 disabled, 미리보기만 액티브
- 우상단 [수정] (EDITOR↑) → editMode 토글, URL 변경 X
- edit 진입 시 dirty 추적 → beforeunload prompt
- [취소] dirty면 confirmDialog 후 reset

#### 미구현 영역(mock)
- **매칭 사용자**(segment_users): 빈 카드 + "Phase 8 출시 예정"
- **재계산**(segment_rebuild): EDITOR↑ 활성. 클릭 → mock BUILDING 반환 → 5s setTimeout invalidate → READY
- **연결 캠페인**(segment_linked_campaigns): 빈 4-탭(active/paused/draft/ended). Sprint 6 구현 후 mock store 연동(P1)

### 2.3 즐겨찾기 + 최근 사용
- GET /segments/favorites 단일 호출 → 두 섹션 동시 렌더
- 칩: ⭐ 이름 · 5,234명, 클릭 시 /segments/:id
- 토글: 낙관적 업데이트 → POST/DELETE /favorite → 실패 시 롤백 + invalidate

---

## 3. 폼 검증 (Zod)
```ts
nameSchema:        z.string().min(1).max(30)
descriptionSchema: z.string().max(100).default("")
operatorSchema:    z.enum(["eq","neq","gt","lt","gte","lte","in","contains"])
periodSchema:      z.discriminatedUnion("type", [
  z.object({ type: z.literal("relative"), days: z.number().int().min(1).max(3650) }),
  z.object({ type: z.literal("absolute"), startDate: z.string().date(), endDate: z.string().date() })
    .refine(d => d.startDate <= d.endDate, "시작일 ≤ 종료일"),
  z.object({ type: z.literal("cumulative") }),
])
conditionNodeSchema: z.lazy(() => z.discriminatedUnion("type", [...4개 leaf, groupNodeSchema]))
groupNodeSchema:     z.object({ logic: z.enum(["and","or"]), conditions: z.array(conditionNodeSchema).min(1) })
createSegmentSchema: z.object({ name, description, conditionTree: groupNodeSchema })
```

z.lazy + recursive discriminated union으로 sealed interface 매핑.

---

## 4. 권한별 차이
| 작업 | VIEWER | EDITOR↑ |
|---|---|---|
| 목록/상세 조회 | ✅ | ✅ |
| 즐겨찾기 토글 | ✅ | ✅ |
| /new 진입 | redirect | ✅ |
| 빌더 수정/저장 | read-only | ✅ |
| preview/rebuild/delete | 비활성 | ✅ |

가드: 인라인 `role !== "VIEWER"` 분기. /new 라우트는 RequireProject 안에서 VIEWER → /segments redirect.

---

## 5. 빈/로딩/에러
- 목록 빈: "첫 세그먼트를 만들어보세요" + CTA(EDITOR↑)
- 빌더 미리보기 로딩: spinner + "예상 모수 계산 중..."
- 저장 에러:
  - SEGMENT_400_NAME_DUPLICATED → 이름 필드 inline
  - SEGMENT_409_LINKED_ACTIVE_CAMPAIGN → 헤더 경고 + 수정·삭제 dim
- preview 실패: inline + [재시도]
- BUILDING 충돌(SEGMENT_409): 토스트 + detail refetch

---

## 6. 접근성
- 노드 카드 tabIndex={0}, Enter→첫 필드 focus, Esc→노드 닫힘
- AND/OR `role="radiogroup"` + `aria-checked`
- beforeunload prompt + 페이지 이탈 시 confirmDialog
- ⭐ aria-label 토글

---

## 7. Frontend Agent 노트

### 7.1 신규 파일
```
src/api/segment.ts
src/api/_mock/segment.ts (rebuild/users/linked-campaigns)
src/hooks/queries/segment.ts
src/schemas/segment.ts (recursive zod)
src/lib/segment-errors.ts (9개 코드)
src/features/segments/Segments.tsx (.jsx 대체)
src/features/segments/SegmentBuilder.tsx
src/features/segments/builder/ConditionTreeEditor.tsx
src/features/segments/builder/GroupNodeEditor.tsx
src/features/segments/builder/EventNodeEditor.tsx
src/features/segments/builder/UserNodeEditor.tsx
src/features/segments/builder/TagNodeEditor.tsx
src/features/segments/builder/DeviceNodeEditor.tsx
src/features/segments/builder/PeriodEditor.tsx
src/features/segments/builder/OperatorSelect.tsx
src/features/segments/builder/PreviewPanel.tsx
src/features/segments/builder/treeReducer.ts
src/features/segments/FavoritesStrip.tsx
```

### 7.2 라우터
- /p/:pid/segments, segments/new, segments/:id 모두 element 교체
- /new에 RequireRole min="EDITOR" wrap (또는 인라인 redirect)

### 7.3 핵심 결정
1. 트리 state는 **useReducer** + path(number[]) 기반 mutation. immer 도입 P1
2. preview 자동 호출 X — 명시적 클릭만
3. 저장 버튼 활성: previewCallCount >= 1, dirty 시 재실행 권장 경고
4. mode 토글: URL 유지, editMode 로컬 state. EDITOR↑ + BUILDING 아님 + linked active 없을 때만 [수정] 활성
5. mock-fallback 분리: rebuild/users/linked-campaigns만 ifMock
6. JsonNode value: op="in"이면 split, 그 외 string|number. zod transform
7. buildStatus 폴링 X — rebuild 후 5s setTimeout invalidate

---

## 8. 위험 & follow-up

| 우선 | 항목 | 담당 |
|---|---|---|
| P0 | EventNode filters JsonNode value 타입 백엔드 직렬화 합의 | Backend |
| P0 | preview estimatedAt timezone(KST/UTC) 명시 | Backend |
| P1 | rebuild mock → 실 API 전환 시 폴링 도입 | Frontend |
| P1 | linked-campaigns Sprint 6 구현 후 mock store 연동 | Frontend |
| P1 | 트리 깊이 제한(3-deep) 합의 | Both |
| P2 | useReducer → immer | Frontend |
| P2 | 모바일 세로 stack | Frontend |

---

**버전**: 1.0
**다음 단계**: Frontend Agent 구현 → CTO 검수
