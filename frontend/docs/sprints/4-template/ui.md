# Sprint 4 — Template UI 설계

> UI/UX Agent. Backend Liaison `docs/sprints/4-template/api.md` + Sprint 1~3 ui.md/review.md 패턴 + 공용 컴포넌트(`src/components/common`) + 기존 mock `Templates.jsx` 골격 기준.
>
> **작성**: 2026-05-01
> **전제**: POST만 실 API. GET 목록/상세, PUT, DELETE는 모두 mock-fallback (`template_list:true` 플래그). 백엔드가 `{{variable}}` 자동 추출. **이미지는 Sprint 4 1차 미지원**. 권한 가드는 프런트 `role !== "VIEWER"` 인라인.

---

## 1. 라우트 매핑
| 경로 | 페이지 | 가드 | 상태 |
|---|---|---|---|
| `/p/:projectId/templates` | `Templates` (.tsx 신규) | RequireAuth + RequireProject + AppLayout | mock 골격 → 실 데이터 + mock-fallback |

**결정**: 기존 3-컬럼 인라인 에디터를 **목록 페이지 + 모달 분리** 구조로 재설계. 사유: dirty-state 관리 단순화, UIBus 패턴 일관, mock/실 API 코드 경로 분리.

---

## 2. 와이어프레임

### 2.1 Templates 목록
```
+----------------------------------------------------------------+
| PageHeader: 메시지 템플릿 / [+ 새 템플릿]                      |
+----------------------------------------------------------------+
| [SearchInput] [채널 ▾] [정렬 ▾]                               |
+----------------------------------------------------------------+
| [카드 그리드 3~4열, 반응형]                                    |
+----------------------------------------------------------------+
| 페이지네이션 (page=0&size=12)                                 |
+----------------------------------------------------------------+
```

카드 구성:
- 1행: 템플릿 이름 + ⋯ 메뉴 (수정/삭제 — EDITOR 이상)
- 2행: 본문 미리보기 2줄 truncate
- 3행: ChannelBadge + 생성자(축약) + 생성일
- 카드 클릭 → openModal({ kind: "template-detail", id })

### 2.2 생성 모달 (실 API)
```
[새 템플릿 만들기]
이름 * / 설명 / 채널 * / 제목 / 본문 * (변수 강조)
[+ {{name}}] [+ {{product_name}}] 변수 미리보기 패널
[취소] [만들기]
```
- 제출: createTemplate({projectId, name, channel, body}) → POST 실행
- 응답 messageTemplateId만 받음 → mock store에 description/title 봉합 저장
- invalidateQueries(["templates", projectId]) + toast + 모달 닫기

### 2.3 상세 모달 (mock)
- 모든 필드 + requiredVariables Tag 칩
- DRAFT만 [수정][삭제] 활성

### 2.4 수정 모달 (mock)
- TemplateFormModal mode="edit", initialValues prefill
- mock store in-memory Map 갱신 + delay(200)

### 2.5 삭제 (mock)
- confirmDialog danger → mock store 제거 + invalidate + toast

---

## 3. 폼 검증 (zod)
```ts
nameSchema: string().min(1).max(50)
channelSchema: enum(["PUSH","EMAIL","SMS","WEBHOOK","KAKAO"])
titleSchema: string().max(100).optional()  // mock-only
bodySchema: string().min(1).max(1000)
descriptionSchema: string().max(200).optional()  // mock-only
```
헬퍼: `extractVariables(body): string[]` — `/\{\{(\w+)\}\}/g` (백엔드와 동일)

---

## 4. 권한별 차이
- VIEWER: 목록/상세까지
- EDITOR/OWNER/ADMIN: 전체
- 가드: `role !== "VIEWER"` 인라인 분기 (백엔드 `@PreAuthorize` 미적용 — 보안 경계 X, UX 분기만)

---

## 5. 빈/로딩/에러
- 빈: "첫 템플릿을 만들어보세요" + CTA(EDITOR+)
- 로딩: 카드 skeleton 6개
- 에러: 카드 그리드 자리 "템플릿을 불러오지 못했습니다 [재시도]"
- 모달 제출 에러: 폼 하단 inline 배너

---

## 6. 접근성
- 모달 role="dialog" + aria-labelledby + autoFocus + 포커스 트랩
- 변수 강조 색상 + aria-describedby로 "감지된 변수: name, ..."
- 카드 tabIndex={0} + Enter
- 삭제 confirm 기본 포커스를 [취소]

---

## 7. Frontend Agent 노트

### 7.1 신규 파일
- `src/api/template.ts` — createTemplate(실), list/get/update/delete(ifMock 래핑)
- `src/api/_mock/template.ts` — in-memory Map<id, Template> + seed 6건
- `src/hooks/queries/template.ts` — useTemplateListQuery, useTemplateDetailQuery, useCreateTemplateMutation, useUpdateTemplateMutation, useDeleteTemplateMutation
- `src/schemas/template.ts` — zod
- `src/features/templates/Templates.tsx` (.jsx 대체)
- `src/features/templates/TemplateFormModal.tsx` (생성/수정 공용)
- `src/features/templates/TemplateDetailModal.tsx`
- `src/features/templates/VariableHighlight.tsx` (textarea 오버레이)

### 7.2 라우터
- `/p/:projectId/templates` element → `<Templates />`

### 7.3 mock store
- 모듈 싱글톤 Map. 새로고침 시 seed 재생성 (세션 한정). localStorage P1
- POST 성공 시 `templateStore.set(messageTemplateId, { ...request, status:"DRAFT", createdAt, createdBy })`

### 7.4 mock-fallback
- 어댑터 레이어가 단일 인터페이스 — 백엔드 추가 구현 시 `ifMock` 호출만 제거

---

## 8. 핵심 결정
1. 이미지 미지원 (1차)
2. mock-fallback 광역화
3. 상태 필드 mock-only (DRAFT)
4. 목록/상세를 모달 분리
5. 변수 추출은 백엔드 권위, 클라는 미리보기용

---

## 9. 위험 & follow-up
1. POST가 messageTemplateId만 반환 → 실제 GET 구현 시 description/title 사라질 수 있음 — Backend Liaison에 필드 추가 요청
2. X-Member-Id 헤더 — Sprint 5 인증 통합 시 제거
3. 프런트 only 가드 — 보안 경계 X
4. mock seed 새로고침 초기화 — localStorage 영속화 P1

---

**버전**: 1.0
**다음 단계**: Frontend Agent 구현 → CTO 검수
