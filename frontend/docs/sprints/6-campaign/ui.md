# Sprint 6 — Campaign UI 설계

> UI/UX Agent. Backend Liaison `docs/sprints/6-campaign/api.md` + Sprint 1~5 ui.md/review.md 패턴 + 기존 mock(`Campaigns.jsx`/`CampaignWizard.jsx`/`CampaignDetail.jsx`/`MobilePreview.jsx`) + 공용 컴포넌트(`src/components/common`) + mock 인프라(`src/api/_mock/index.ts`, `campaign:true` 플래그) 기준.
>
> **작성**: 2026-05-02
> **전제**: 7개 엔드포인트 전부 mock-fallback. CampaignType 2종(ONE_TIME/AUTOMATION) × Channel 5종(PUSH/EMAIL P0, SMS/WEBHOOK/KAKAO P1) × SendMode 3종(IMMEDIATE/SCHEDULED/RECURRING) polymorphic schedule. CampaignStatus 4종(DRAFT/ACTIVE/PAUSED/ENDED). Sprint 4 templates / Sprint 5 segments 훅 재사용.

---

## 1. 라우트 매핑

| 경로 | 페이지 | 가드 |
|---|---|---|
| `/p/:projectId/campaigns` | `Campaigns.tsx` | RequireAuth + RequireProject + AppLayout |
| `/p/:projectId/campaigns/new` | `CampaignWizard.tsx` (mode="create") | EDITOR↑ |
| `/p/:projectId/campaigns/:campaignId` | `CampaignDetail.tsx` | VIEWER↑ 진입, EDITOR↑ 액션 |

위저드는 단일 라우트 + step state. DRAFT 수정도 CampaignWizard 재사용 (mode="edit" + initialCampaignId prop).

---

## 2. 와이어프레임

### 2.1 Campaigns 목록
```
+----------------------------------------------------------------+
| PageHeader: 캠페인 / [+ 새 캠페인] (EDITOR↑)                   |
+----------------------------------------------------------------+
| 탭: 전체 | DRAFT | ACTIVE | PAUSED | ENDED                     |
| [SearchInput] [채널 ▾] [정렬 ▾]                                |
+----------------------------------------------------------------+
| 테이블: 이름/설명 | 상태 | 채널 | 세그먼트 | 발송수 | 오픈률 |  |
|        다음발송 | ⋯                                            |
+----------------------------------------------------------------+
```
- ⋯ 메뉴: 시작(DRAFT) / 일시정지(ACTIVE) / 재개(PAUSED, P1) / 복제(P1) / 삭제(DRAFT)
- 빈 상태: "첫 캠페인을 만들어보세요" + CTA(EDITOR↑)

### 2.2 CampaignWizard (6단계)
좌측 stepper(240px sticky) + 우측 step body. 헤더에 진행률.

#### Step 1 — 기본 정보
- 이름 * (max 50, CAMPAIGN_004)
- 유형 라디오 (ONE_TIME / AUTOMATION dim+P1)
- 설명 textarea (max 200, CAMPAIGN_005)

#### Step 2 — 채널 선택
- 5개 채널 카드 (PUSH/EMAIL P0 활성, SMS/WEBHOOK/KAKAO P1 disabled)
- **단일 선택 강제** (백엔드 entity `channel` 단수). 다중은 P1

#### Step 3 — 세그먼트 선택
- 검색 + 즐겨찾기 칩 (Sprint 5 useFavoritesQuery)
- 카드 리스트 (예상모수 buildStatus===READY만, 캠페인 연결수)
- 단일 선택 + "전체 사용자 발송"(segmentId=null) 옵션

#### Step 4 — 발송 일정
SendMode 라디오 3종:
- **IMMEDIATE**: 추가 입력 없음
- **SCHEDULED**: `<input type="datetime-local">`, 과거 시각 차단(CAMPAIGN_008), 클라가 toUtcIso 변환
- **RECURRING**:
  - repeatType (DAILY/WEEKLY/MONTHLY)
  - repeatTime (HH:mm)
  - WEEKLY: 요일 칩 다중(MON~SUN, ≥1)
  - MONTHLY: 일자 (1~31)
  - recurringCount (≥1)
  - cron 미리보기 (RecurringCronGenerator JS 포팅, 참고용)

#### Step 5 — 템플릿 선택
- Sprint 4 useTemplateListQuery({channel: data.channel, size:12})
- 카드 그리드 + 우측 MobilePreview (변수 강조)
- [+ 새 템플릿] 인라인 모달 (Sprint 4 TemplateFormModal 재사용)

#### Step 6 — 확인 + 저장
- 좌측 요약 카드 (편집 → step 점프) + 우측 MobilePreview
- 예상 발송 비용 카드
- 버튼: [DRAFT로 저장] / [생성 후 시작]
  - [생성 후 시작]: confirmDialog "발송이 즉시 시작됩니다" → POST + start 연쇄
  - SCHEDULED/RECURRING은 [생성 후 활성화]

### 2.3 CampaignDetail
```
+----------------------------------------------------------------+
| 헤더: 이름 + 상태배지 + 채널배지 + actions                     |
+----------------------------------------------------------------+
| StatCard 4: 발송수 / 오픈 / 클릭 / 전환 (analytics mock)        |
+----------------------------------------------------------------+
| 좌(1fr): 기본정보 / 채널·세그먼트 / 템플릿 / 일정 / 실행 이력  |
| 우(340px sticky): MobilePreview                                |
+----------------------------------------------------------------+
```

#### 액션 매트릭스
| Status | 수정 | 삭제 | 발송시작 | 일시정지 | 재개(P1) | 종료(P1) |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| DRAFT | ✓ | ✓ | ✓ | - | - | - |
| ACTIVE | - | - | - | ✓ | - | ✓ |
| PAUSED | - | - | - | - | ✓ | ✓ |
| ENDED | - | - | - | - | - | - |

VIEWER 모든 액션 비활성. confirmDialog 후 mutation.

#### 실행 이력 테이블
- mock store campaignId 기준 CampaignExecution[]
- 회차 / 상태 Tag(READY/SEND/SUCCESS/FAIL) / 대상 / 발송 / 실패 / 시각 / 실패사유

---

## 3. 폼 검증 (Zod)
```ts
nameSchema: z.string().min(1).max(50)
descSchema: z.string().min(1).max(200)
campaignTypeSchema: z.enum(["ONE_TIME","AUTOMATION"])
channelSchema: z.enum(["PUSH","EMAIL","SMS","WEBHOOK","KAKAO"])
segmentIdSchema: z.number().int().nullable()

scheduleSchema = z.discriminatedUnion("type",[
  z.object({ type: z.literal("immediate") }),
  z.object({ type: z.literal("scheduled"), scheduledAt: z.string().datetime() })
    .refine(d => new Date(d.scheduledAt) > new Date(), "과거 시각"),
  z.object({ type: z.literal("recurring"),
             repeatType: z.enum(["DAILY","WEEKLY","MONTHLY"]),
             repeatTime: z.string().regex(/^\d{2}:\d{2}/),
             repeatDayOfWeek: z.string().optional(),
             repeatDayOfMonth: z.number().int().min(1).max(31).optional(),
             recurringCount: z.number().int().min(1) })
    .superRefine(weeklyRequiresDow + monthlyRequiresDom),
])
createCampaignSchema = z.object({
  campaignName, campaignDescription, campaignType,
  channel, segmentId, messageTemplateId: z.number().int(),
  schedule: scheduleSchema, isTempSave: z.boolean(),
})
```
스텝별 부분 검증 (step1Schema~step6Schema).

---

## 4. 권한별 차이
| 작업 | VIEWER | EDITOR↑ |
|---|:-:|:-:|
| 목록/상세 조회 | ✓ | ✓ |
| /new 진입 | redirect | ✓ |
| 위저드 저장 | - | ✓ |
| start/pause/delete | dim | ✓ |

---

## 5. 빈/로딩/에러
- 위저드 [다음] disabled + inline 에러 (스텝 변경 토스트 "필수 항목 확인")
- CAMPAIGN_001~014 → 해당 step 자동 점프 + 필드 inline
- OPTIMISTIC_LOCK_FAILURE → "다른 사용자가 수정했습니다 [새로고침]"
- CAMPAIGN_ALREADY_ACTIVE → 토스트 + invalidate
- analytics getCampaignStats: campaign mock-fallback 시 더미 반환 분기 필요

---

## 6. 접근성
- 위저드 step autoFocus
- 라디오 그룹 role="radiogroup" + aria-checked
- WEEKLY 요일 칩 role="checkbox" + aria-pressed
- confirmDialog 기본 포커스 [취소], danger 버튼 [발송 시작]/[일시정지]
- beforeunload prompt
- progress aria-valuenow

---

## 7. Frontend Agent 노트

### 7.1 신규 파일
```
src/api/campaign.ts
src/api/_mock/campaign.ts            # store + 7 endpoint handlers
src/hooks/queries/campaign.ts
src/schemas/campaign.ts              # discriminated union schedule
src/lib/campaign-errors.ts           # 16개 코드
src/lib/cron-preview.ts              # RecurringCronGenerator JS 포팅
src/features/campaigns/Campaigns.tsx          # .jsx 대체
src/features/campaigns/CampaignWizard.tsx
src/features/campaigns/wizard/Step1Basic.tsx
src/features/campaigns/wizard/Step2Channels.tsx
src/features/campaigns/wizard/Step3Segment.tsx
src/features/campaigns/wizard/Step4Schedule.tsx
src/features/campaigns/wizard/Step5Template.tsx
src/features/campaigns/wizard/Step6Review.tsx
src/features/campaigns/wizard/Stepper.tsx
src/features/campaigns/wizard/wizardReducer.ts
src/features/campaigns/CampaignDetail.tsx
src/features/campaigns/ExecutionsTable.tsx
src/features/campaigns/MobilePreviewPane.tsx  # 기존 .jsx → .tsx 변환
```

### 7.2 라우터
- 3개 path element 교체. /new에 EDITOR↑ 인라인 redirect

### 7.3 mock store
- Map<campaignId, CampaignRecord> + Map<campaignId, CampaignExecution[]>
- seed 8건: DRAFT 2 / ACTIVE 3(IMMEDIATE+SCHEDULED+RECURRING) / PAUSED 1 / ENDED 2
- segmentId는 Sprint 5 seed id 매칭, messageTemplateId는 Sprint 4 seed 매칭
- 상태 전이 헬퍼 + start 시뮬: READY → 0.5s SEND → 1.5s SUCCESS
- recurringCron 클라 산출 후 record 저장
- 검증 throw → CampaignErrorCode

### 7.4 Sprint 5 segment 연동 (P1 follow-up 처리)
- `_mock/segment.ts` getLinkedCampaignsMock 갱신: campaign store import → segmentId 매칭 → 4-tab(active/paused/draft/ended)
- 순환 import 방지: campaign → segment 단방향, segment에서 campaign getter만 호출

### 7.5 핵심 결정
1. **단일 채널** 강제. 다중은 P1
2. SCHEDULED: native datetime-local, KST→UTC 클라 책임
3. RECURRING cron: 백엔드 권위, 프런트는 미리보기 참고용
4. CampaignWizard 단일 라우트 + step state (useReducer)
5. CampaignStatus/ChannelBadge enum 매핑 보강 필요(DRAFT/ACTIVE/PAUSED/ENDED, PUSH/EMAIL/...)
6. analytics getCampaignStats: campaign mock-fallback 시 더미 분기 필요
7. mock store 새로고침 휘발성. localStorage P2

---

## 8. 위험 & follow-up

| 우선 | 항목 | 담당 |
|---|---|---|
| P0 | 단일/다중 채널 백엔드 정책 확정 | Backend |
| P0 | scheduledAt UTC ISO 직렬화 합의 | Backend |
| P0 | CampaignStatus/ChannelBadge enum 매핑 보강 | Frontend |
| P1 | edit 라우트 정식 분리(`/campaigns/:id/edit`) | Frontend |
| P1 | execution 폴링(Sprint 9 WebSocket) | Both |
| P1 | analytics campaign mock-fallback 분기 | Frontend |
| P2 | mock localStorage 영속화 | Frontend |
| P2 | 다중 채널 매핑 UI | Both |

---

**버전**: 1.0
**다음 단계**: Frontend Agent 구현 → CTO 검수
