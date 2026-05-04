# Sprint 7 — Event/Notification/Audit UI 설계

> UI/UX Agent. Backend Liaison `docs/sprints/7-monitoring/api.md` + Sprint 1~6 패턴 + 기존 mock(`Events.jsx`/`EventDetail.jsx`/`SdkGuideModal.jsx`/`AddEventCategoryModal.jsx`/`AuditSettings.jsx`) + 공용 컴포넌트 + mock 인프라(`event_categories:true`/`event_schema:true` 활성).
>
> **작성**: 2026-05-02
> **전제**: Event POST collect만 실 API(SDK 전용). 카테고리/스키마/목록/검색은 mock-fallback. Notification 8개·Audit 2개는 백엔드 완성. Audit는 OWNER↑(ADMIN 포함).

---

## 1. 라우트 매핑

| 경로 | 페이지 | 가드 |
|---|---|---|
| `/p/:pid/events` | `Events.tsx` | placeholder → 교체 |
| `/p/:pid/events/:eventName` | `EventDetail.tsx` | placeholder → 교체 |
| `/p/:pid/campaigns/:cid?tab=logs` | `CampaignDetail.tsx` 안 NotificationLogs 탭 | Sprint 6 구조 확장 |
| `/p/:pid/settings?tab=audit` | `AuditTab.tsx` (settings 탭 신규) | OWNER↑ 인라인 분기 |
| `/admin/audit` | `AdminAuditLogs.tsx` | RequireAdmin |

**결정**:
1. 알림 로그는 캠페인 상세 안 탭 통합. 사용자별 검색 페이지는 P1
2. 감사 로그는 두 진입점·한 컴포넌트 (`AuditLogTable scope="project"|"admin"`)
3. Event 카테고리/스키마는 mock 유지

---

## 2. 페이지 와이어프레임

### 2.1 Events 목록
```
PageHeader: 이벤트 / [SDK 가이드][+ 카테고리 추가] (EDITOR↑)
StatCard×4: 총 카테고리/30일 누적 수집/성공률/이상감지
탭칩: 전체|기본|커스텀  검색  상태 ▾
테이블: 이름 | 유형 | 30일 수집량 | 성공률 | 최근수집 | 상태 ⋯
```
기존 골격 유지. mock seed 5종(purchase/add_to_cart/page_view/login/signup). ⋯ 메뉴: 편집/삭제(EDITOR↑, 사용 중인 세그먼트 0건일 때만).

### 2.2 EventDetail
```
StatCard×4: 30일 수집량/성공률/고유 사용자/마지막 수신
좌(1fr): 속성 스키마 테이블 (이름/타입/필수/예시/⋯) [+ 속성 추가]
우(380px): 최근 수신 샘플 JSON / 사용 중인 세그먼트
```
스키마 편집은 mock. 변경 시 confirmDialog "사용 세그먼트 N개 모수 재계산". `getLinkedSegmentsByEvent` 헬퍼는 P1.

### 2.3 NotificationLogs — Campaign 상세 안 탭
Sprint 6 CampaignDetail 확장. URL: `?tab=overview|logs`
```
필터: 채널 ▾ | 상태 ▾ | 날짜 범위 | userId 검색
테이블: userId | 채널 | 상태 | 발송 | 전달 | 클릭 | 오류 ⋯
페이지네이션
행 클릭 → NotificationLogDetailModal
```
상태 색상: SENT(default)/DELIVERED(success)/CLICKED(info)/FAILED(error)
errorCode Tag(error tone) — `FCM_INVALID_TOKEN` 등
상세 모달: errorMessage / recipient(마스킹) / userAgent

API: `GET /api/projects/{pid}/campaigns/{cid}/logs?channel=&status=&page=&size=`

### 2.4 AuditLogs — 두 진입점, 한 컴포넌트
```
Card: 감사 로그 (append-only, 수정/삭제 불가 안내)
필터: action ▾ | targetType ▾ | 날짜 범위 | memberId 검색
       (ADMIN: projectId ▾ 추가)
테이블: 시각 | 멤버 | action | 대상 | IP | ⋯
행 클릭 → AuditLogDetailModal (detail JSON pretty + UA)
```

- Settings tab=audit (OWNER): projectId 고정, 필터 숨김
- /admin/audit (ADMIN): projectId 필터 노출
- @Immutable 안내 카드 헤더에 명시. 수정/삭제 액션 UI 제거. ⋯ 메뉴는 "복사"만

---

## 3. 폼 검증 (Zod)
```ts
// event
categoryNameSchema = z.string().regex(/^[a-z_][a-z0-9_]{2,39}$/)
displayNameSchema = z.string().min(1).max(50)
propertyKeySchema = z.string().regex(/^[a-z_][a-z0-9_]{0,39}$/)
propertyTypeSchema = z.enum(["string","number","boolean","datetime","object"])

// notification
channelSchema = z.enum(["PUSH","EMAIL","SMS","WEBHOOK"])
deliveryStatusSchema = z.enum(["SENT","DELIVERED","FAILED","CLICKED"])

// audit
auditActionSchema = z.enum(["CREATE","UPDATE","DELETE","READ","EXPORT","LOGIN","ROLE_CHANGE"])
auditTargetTypeSchema = z.enum(["CAMPAIGN","SEGMENT","TEMPLATE","MEMBER","API_KEY","CREDENTIAL","PROJECT"])
dateRangeSchema = z.object({ startDate, endDate }).refine(d => d.start <= d.end)
```

---

## 4. 권한별 차이

| 작업 | VIEWER | EDITOR | OWNER | ADMIN |
|---|:-:|:-:|:-:|:-:|
| 이벤트 목록/상세 | ✓ | ✓ | ✓ | ✓ |
| 카테고리/스키마 변경 (mock) | - | ✓ | ✓ | ✓ |
| 알림 로그 조회 | ✓ | ✓ | ✓ | ✓ |
| 감사 로그 (자기 프로젝트) | - | - | ✓ | ✓ |
| 감사 로그 가로 조회 | - | - | - | ✓ |

AuditTab은 항상 노출하고 마운트 시 권한 가드 (DenyView).

---

## 5. 빈/로딩/에러

- 첫 카테고리: "첫 이벤트 카테고리를 추가하고 SDK로 수집을 시작하세요"
- 알림 로그 0: "이 캠페인에 발송 이력이 없습니다" + (DRAFT면) "캠페인을 시작하면 로그가 쌓입니다"
- 감사 로그 0: "선택 조건에 해당하는 감사 로그가 없습니다"
- 권한 부족: DenyView "감사 로그 열람은 OWNER 이상"
- 에러: LOG_NOT_FOUND/AUDIT_NOT_FOUND/INVALID_DATE_RANGE/FCM_*

---

## 6. 접근성
- 테이블 행 tabIndex={0} + Enter/Space 모달
- 상세 모달 포커스 트랩 + ESC
- Settings/Campaign 탭 role="tablist" + 좌우/Home/End
- 시각/IP/UA mono — `<time dateTime>`

---

## 7. Frontend Agent 노트

### 7.1 신규 파일
```
src/api/event.ts
src/api/_mock/event.ts (카테고리/스키마 store)
src/api/notification.ts (8 실 API)
src/api/audit.ts (2 실 API)
src/hooks/queries/{event,notification,audit}.ts
src/schemas/{event,notification,audit}.ts
src/lib/{event,notification,audit}-errors.ts
src/features/events/{Events,EventDetail,AddEventCategoryModal,EditSchemaModal,SdkGuideModal}.tsx
src/features/notifications/{NotificationLogTable,NotificationLogDetailModal,notification-status.ts}
src/features/audit/{AuditLogTable,AuditLogDetailModal,AdminAuditLogs}.tsx
src/features/settings/tabs/AuditTab.tsx
```

### 7.2 라우터
- `/p/:pid/events`, `events/:eventName` 교체
- `/admin` children + `{ path: "audit", element: <AdminAuditLogs /> }`
- SettingsLayout TABS에 audit 탭 추가
- CampaignDetail에 `?tab=logs` 분기

### 7.3 mock store (`_mock/event.ts`)
- Map<categoryId, EventCategory> + Map<categoryName, PropertySchema[]>
- 5종 시드 (purchase 7속성/add_to_cart 4/page_view 3/login 2/signup 3)
- 임의 eventCount(50K~200K), lastCollectedAt
- mutation은 store 직접 갱신

### 7.4 알림/감사 — 백엔드 직결
- notification.ts/audit.ts 모두 api.get 직접
- DOMAIN_FLAGS에 키 추가 안 함 (전부 false 동작)
- ApiResponse unwrap 동일

### 7.5 핵심 결정
1. 알림 로그는 캠페인 상세 탭. 사용자별 페이지는 P1
2. 감사 로그 두 진입점, 한 컴포넌트 (`AuditLogTable scope` prop)
3. Event 카테고리/스키마 mock 유지
4. SdkGuideModal .jsx → .tsx, Public API 키는 Sprint 2 useApiKeysQuery 동적 치환
5. Audit @Immutable 안내 + 수정/삭제 UI 제거
6. Notification credential CRUD는 Sprint 7 범위 밖 (기존 FcmSettings.jsx 잔존, 후속 settings 통합)

---

## 8. 위험 & follow-up

| 우선 | 항목 | 담당 |
|:-:|---|---|
| P0 | Audit action/targetType enum 백엔드 확정값 공유 | Backend |
| P0 | NotificationLog status enum 확정 | Backend |
| P0 | Audit memberName/memberEmail null 케이스 정책 | Both |
| P1 | 사용자별 알림 로그 페이지 | FE |
| P1 | Event 카테고리/스키마 백엔드 → mock 플래그 제거 | Backend |
| P1 | NotificationLog CSV 내보내기 | FE |
| P1 | Audit detail diff 시각화 | FE |
| P2 | FcmSettings.jsx → settings tab=channels 통합 | FE |

---

**버전**: 1.0
**다음 단계**: Frontend Agent 구현 → CTO 검수 → 프로젝트 마감
