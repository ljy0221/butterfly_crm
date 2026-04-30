---
status: accepted
date: 2026-04-29
decision-makers: 역할 6 (분석/통합 검증)
consulted: 역할 1 (인증/권한), 역할 4 (캠페인/메시지), 역할 5 (발송 워커), 역할 3 (고객/이벤트)
related: ADR-0001, ADR-0002, ADR-0006, ADR-0007, ADR-0008, ADR-0009
jira: S14P31B101-47
---

# ADR-0010: 저장 전략·반정규화 기준 — 도메인 횡단 가이드

## Context and Problem Statement

요구명세서(§12)와 기획서는 `Tenant / Project / Customer / Segment / Campaign / CampaignMessage / Flow / FlowNode / DeliveryJob / DeliveryLog / ConversionEvent` 등 다수 엔터티를 정의한다. 그러나 실제 ERD([`backend/docs/erd.md`](../erd.md))와 모듈별 DDL 사이에 다음 균열이 누적되었다:

1. **요구명세 vs 구현 사이의 누락** — `CampaignMessage`, `Flow`, `FlowNode`는 요구명세에 있지만 ERD/엔티티 코드에는 존재하지 않는다. "도입할 것인지, 도입한다면 어떤 형태로 정규화할 것인지"가 결정되지 않은 상태.
2. **반정규화의 일관성 부재** — 분석 모듈은 `project_id`, `campaign_id`를 로그/집계에 적극적으로 비정규화하지만(ADR-0007 기준), 캠페인/세그먼트는 그렇지 않다. 어떤 컬럼이 "성능 비정규화", 어떤 것이 "스냅샷"인지 구분 기준이 명문화되어 있지 않다.
3. **스냅샷 컬럼 추가 기준 부재** — `notification_logs.message_template_id`는 발송 시점 템플릿을 추적하기 위한 스냅샷 컬럼이고, `segment_snapshots`는 회차별 모수를 보존하는 스냅샷 테이블이다. 그러나 어떤 데이터를 발송 시점에 "고정(snapshot)"하고 어떤 데이터를 정규화 참조로 둘지에 대한 가이드가 없다.

ADR-0007이 분석 도메인 한정 결정을 다뤘다면, 본 ADR은 **도메인 횡단으로 통용되는 저장 전략·반정규화 기준**을 한 자리에 모아 후속 구현/리뷰의 단일 기준점으로 삼는다.

## Decision Drivers

- 멀티테넌시 격리: `project_id`는 **항상 직접 보유**한다 (모듈 경계 위에서 강제 격리에 사용)
- 발송 이력의 재현 가능성: 캠페인/템플릿/세그먼트가 사후에 변경되어도 과거 발송이 어떤 메시지·대상자로 나갔는지를 그대로 재현해야 한다
- 집계의 멱등성: 자연 키 UPSERT를 보장할 수 있는 형태로 비정규화한다
- 운영 단순성: MVP 발표 전까지는 "있으면 좋은" 정규화/엔터티는 도입하지 않는다 (FlowNode 등)
- 모듈 경계 보호: 한 도메인이 다른 도메인 테이블을 JOIN으로 매번 끌어오는 구조를 피한다

## Considered Options

### Option A — 요구명세 그대로 모든 엔터티를 ERD에 즉시 도입

`CampaignMessage`, `Flow`, `FlowNode`, `ConversionEvent` 등을 모두 별도 테이블로 만들고 정규화 우선으로 간다.

- 👍 요구명세와 1:1 대응되어 추적성이 좋다
- 👎 MVP 스코프 폭증. Flow 엔진이 없는 상태에서 빈 테이블 5종 추가
- 👎 "사용되지 않는 테이블"은 운영 노이즈가 됨 (마이그레이션, 백업 대상)

### Option B — 인라인/스냅샷으로 흡수 + 향후 도입 시 가이드만 명문화 (채택)

`CampaignMessage` 개념은 `notification_logs`의 인라인 컬럼(`message_template_id`, 향후 `rendered_title/body`)으로 흡수, `Flow/FlowNode`는 MVP 스코프 외로 명시적 보류, 그 외 모든 도메인의 정규화/비정규화 기준을 본 ADR에 일괄 정리한다.

- 👍 MVP 스코프 유지
- 👍 향후 도입 시 따를 기준이 본 문서로 일관됨
- 👎 요구명세와 ERD 사이의 추적성을 본 문서가 직접 매핑해야 함 → 본 ADR의 §"요구명세 매핑"으로 해결

### Option C — 도메인별 ADR을 추가로 분리 작성

캠페인 ADR, 고객 ADR, 발송 ADR을 각각 신규 작성.

- 👍 도메인별 깊이 있는 결정
- 👎 횡단 일관성을 누가 보증할지 불분명
- 👎 4개 ADR로 분산되면 PR 리뷰 시 한 번에 보기 어렵다

## Decision Outcome

**선택: Option B (횡단 가이드 단일 ADR)**.

본 ADR은 두 가지 역할을 동시에 수행한다:
1. **결정 기록** — `CampaignMessage`/`Flow`/`FlowNode` 도입 여부 + 도메인별 반정규화 채택 여부
2. **가이드** — 향후 스냅샷 컬럼/비정규화 도입 시 따라야 할 기준

---

## 1. 저장 전략의 3계층 분류

모든 테이블을 다음 3계층 중 하나로 분류한다.

| 계층 | 정의 | 변경 정책 | 예시 |
|---|---|---|---|
| **원장 (Source of Truth)** | 사용자 의도/사실의 1차 기록. 사라지면 복원 불가 | 도메인 규칙에 따른 UPDATE 허용. 비즈니스 hard delete 금지 (soft delete만) | `members`, `projects`, `customers`(=`users`), `segments`, `campaigns`, `message_templates` |
| **이벤트/로그 (Append-mostly)** | 도메인 사건의 발생 시점 기록 | INSERT only (예외: `notification_logs`의 status 전이만 UPDATE). 트리거/권한으로 강제 | `notification_logs`, `user_action_logs`, `audit_logs`, `events` |
| **파생 (Derived/Cache)** | 원장 + 로그에서 재계산 가능한 사본 | 자연 키 UPSERT, 멱등 재실행 가능 | `*_stats_*`, `event_daily_agg`, `segment_members`, `segment_snapshots` |

**원칙**: 파생 계층이 깨지면 원장+로그에서 재구축할 수 있어야 한다. 파생만 보유한 데이터는 존재해선 안 된다.

---

## 2. 멀티테넌시 비정규화 — `project_id` 직접 보유

**규칙**: 모든 테이블은 `project_id` 컬럼을 직접 보유한다. 단, 다음 두 경우만 예외:
- `members`, `applications` — 회원은 프로젝트보다 상위 개체
- `projects` 자기 자신
- `audit_logs.project_id NULL` — 시스템 레벨 작업(슈퍼 관리자 신청서 승인 등). 명시적 NULL 허용

**근거**:
- 보조 인덱스 선두 컬럼으로 사용해 테넌트 격리 쿼리 풀스캔을 막는다
- 어플리케이션 레이어에서 `@PreAuthorize` 검증 + WHERE 절에 `project_id = #projectId` 강제(ADR-0005 연계)
- 프로젝트 단위 백업/마이그레이션/삭제 시 단일 컬럼으로 필터 가능

**기각된 대안**: "FK를 거슬러 올라가서 project_id에 도달 가능하므로 필요시 JOIN으로 끌어오자"
- 매번 2~3 hop JOIN이 들어가서 인덱스 전략이 복잡해짐
- 모듈 경계를 넘는 조회 시 다른 도메인 테이블을 매번 끌어와야 함

---

## 3. 발송 시점 스냅샷 — 인라인 컬럼 vs 별도 테이블

### 3.1 인라인 스냅샷 (notification_logs 내부)

**채택**: 다음 컬럼을 발송 시점에 고정해 `notification_logs`에 직접 저장한다.

| 컬럼 | 출처 | 이유 |
|---|---|---|
| `campaign_id` | 캠페인 정의 | 캠페인 soft delete돼도 이력 유지 |
| `campaign_execution_id` | 회차 ID (ADR-0002) | 반복 캠페인 회차별 드릴다운 |
| `message_template_id` | 발송 시점 템플릿 | 템플릿 수정 후에도 "그 회차에 어떤 템플릿이 나갔는지" 추적 |
| `channel` | 발송 채널 | 멀티채널 캠페인의 채널별 분리 카운트 |
| `error_code` | 채널 에러 정규화값 | 채널별 코드를 정규화해 실패 분석 일관성 확보 |

**근거**: 이들은 **변경 가능성이 있는 마스터 데이터를 참조**하기 때문에 발송 시점에 고정하지 않으면 통계가 시점에 따라 흔들린다.

### 3.2 별도 스냅샷 테이블 (`segment_snapshots`)

**채택 사유 (대량/구조화된 데이터)**: 회차별 세그먼트 모수는 인라인이 불가능(N rows)하므로 별도 테이블.

```
campaign_executions (1) ─── (1) segment_snapshots ─── (N) segment_snapshot_users
```

**경계**: 인라인은 1행 내 N개 컬럼으로 끝나는 단순 값, 스냅샷 테이블은 N행 이상의 자식 데이터.

### 3.3 `CampaignMessage` 엔터티 — 보류 (Option B 흡수)

요구명세 §12-1의 `CampaignMessage`("발송 직전 메시지 확정")는 **별도 테이블로 도입하지 않는다**. 다음으로 동등 효과를 달성한다:

- 발송 시점 템플릿: `notification_logs.message_template_id` (3.1 인라인 스냅샷)
- 변수 치환 결과(렌더링된 본문): MVP에서는 미저장. **개인정보 보호 측면**에서 본문은 발송 후 보유하지 않음
- A/B 테스트 메시지 버전: MVP 스코프 외 (요구명세 §3-4 선택 기능)

**향후 도입 트리거**: A/B 테스트가 정식 요구로 들어오거나, 본문 재현이 감사 요건으로 추가될 때. 그 시점에 `campaign_messages` 테이블을 도입하고, `notification_logs.campaign_message_id`를 추가한다(`message_template_id`는 유지하되 nullable로 약화).

### 3.4 `Flow` / `FlowNode` — MVP 스코프 외

요구명세 §12-1의 `Flow`/`FlowNode`/`FlowExecution`은 **자동화 플로우 엔진**의 산물이다. 현재 캠페인 도메인에는 `CampaignType.AUTOMATION` enum 값만 존재하고 실행 엔진 구현은 없다.

**결정**: MVP에서는 도입하지 않는다. ERD에도 추가하지 않는다.

**향후 도입 시 따를 원칙**:
- `flow_nodes`는 정규화 우선 (한 Flow가 다수 노드 보유)
- 노드별 설정은 JSON 컬럼으로 저장 (ADR-0006 세그먼트 조건과 동일 패턴)
- 노드 간 엣지(`flow_edges`)는 별도 테이블 — 그래프 트래버설을 위해 정규화 필수
- 실행 이력(`flow_executions`)은 발송 도메인의 `notification_logs`로 흡수 가능 여부를 먼저 검토 (불가하면 별도 테이블)

---

## 4. 도메인별 저장 전략 — 현황 매핑

### 4.1 인증/회원 (역할 1)

| 테이블 | 계층 | 비정규화 컬럼 | 비고 |
|---|---|---|---|
| `members` | 원장 | — | 멤버는 프로젝트보다 상위 |
| `applications` | 원장 | `phone`, `birth_date` 직접 컬럼 | members FK 분리 가능했지만 신청서 단계의 입력값을 그대로 보존 |
| `projects` | 원장 | `tenant_id` (있다면) | 멀티 회사 운영 시 |
| `refresh_tokens`, `token_blacklist` | 이벤트/로그 | `member_id` | TTL 관리 |

**저장 전략 결정**: 정규화 우선. 비정규화 없음.

### 4.2 고객/기기/이벤트 (역할 3)

| 테이블 | 계층 | 비정규화 컬럼 | 비고 |
|---|---|---|---|
| `users` (=Customer) | 원장 | `project_id` | 외부 ID(`external_user_id`)와 내부 ID 분리 (요구명세 §12-3) |
| `devices` | 원장 | `project_id`, `user_id` | 한 user가 N device 보유 |
| `events` | 이벤트/로그 | `project_id`, `user_id`, `device_id` | 원본 보존, INSERT only (요구명세 §12-3) |
| `event_categories`, `event_schemas`, `event_schema_fields` | 원장 | `project_id` | 이벤트 메타데이터 |
| `event_daily_agg` | 파생 | `project_id` | 자연 키 UPSERT |
| `tag_definitions`, `user_tags` | 원장 | `project_id` | 사용자 태그 시스템 |

**저장 전략 결정**:
- `events.payload`는 JSON 컬럼으로 원본 보존. 스키마 검증은 `event_schemas`로 별도 메타 관리
- `events`는 파티셔닝 후보 (월 단위 RANGE)
- `event_daily_agg`로 세그먼트 평가 시 풀스캔 회피

### 4.3 세그먼트 (역할 3)

| 테이블 | 계층 | 비정규화 컬럼 | 비고 |
|---|---|---|---|
| `segments` | 원장 | `project_id` | `condition` JSON 컬럼 (ADR-0006) |
| `segment_members` | 파생 | `project_id`, `segment_id` | 평가 결과 캐시. UPSERT |
| `segment_snapshots` | 이벤트/로그 | `project_id`, `campaign_execution_id` | 회차별 모수 캡처, 영구 보존 |
| `segment_snapshot_users` | 이벤트/로그 | — | snapshot의 자식 |
| `segment_favorites` | 원장 | `project_id`, `member_id` | 즐겨찾기 |

**저장 전략 결정**:
- 조건 트리는 별도 테이블로 정규화하지 않고 **JSON으로 인라인 저장**(ADR-0001/0006)
- 평가 결과(`segment_members`)는 파생 — 언제든 재평가 가능
- 모수(`segment_snapshots`)는 회차 단위 영구 보존 — 캠페인 소급 분석에 필요

### 4.4 캠페인/템플릿 (역할 4)

| 테이블 | 계층 | 비정규화 컬럼 | 비고 |
|---|---|---|---|
| `message_templates` | 원장 | `project_id`, `member_id`(생성자) | 본문/제목 변경 가능, soft delete |
| `campaigns` | 원장 | `project_id`, `message_template_id` 참조, `segment_id` nullable | 정의/실행 분리 (ADR-0002) |
| `campaigns.recurring_cron` + `repeat_*` 컬럼 | 이중 표현 | — | cron 표현식과 정규화 컬럼이 공존. **DRY 위반이지만 의도적**: cron은 스케줄러용, 정규화 컬럼은 필터/통계용 |
| `campaign_executions` | 이벤트/로그 (회차별) | `campaign_id` | 회차마다 1행, soft delete |

**저장 전략 결정**:
- `campaigns.channel`은 `message_templates.channel`과 중복(파생). **유지**: 캠페인 생성 시 채널 고정, 템플릿 변경에도 캠페인 채널은 유지되어야 함 → **스냅샷 컬럼**
- `campaign_executions.target_count/sent_count/failed_count`는 `notification_logs`에서 재계산 가능한 파생값. **유지**: 회차 카드 UI에서 즉시 표시하기 위한 hot path 캐시. 정합성은 `send_reconciliations`(ADR-0007)로 검증
- `CampaignMessage` 별도 테이블 미도입 (§3.3)

### 4.5 발송/사용자 행동 (역할 5)

| 테이블 | 계층 | 비정규화 컬럼 | 비고 |
|---|---|---|---|
| `notification_logs` | 이벤트/로그 | `project_id`, `campaign_id`, `campaign_execution_id`, `message_template_id`, `channel` | 발송 시점 스냅샷 다수 (§3.1) |
| `user_action_logs` | 이벤트/로그 | `project_id`, `campaign_id`, `log_id` (parent FK) | INSERT only |

**저장 전략 결정**:
- `notification_logs`는 status 전이를 위한 UPDATE만 허용 (PENDING → SENT → DELIVERED / FAILED)
- `user_action_logs.project_id`, `campaign_id`는 **`notification_logs`에서 복사**해 INSERT 시 채운다 (애플리케이션 책임, SCHEMA_DESIGN.md Rule 2)
- `DeliveryJob`/`DeliveryLog` (요구명세 §12-1)는 본 두 테이블로 흡수. 별도 큐 메시지 영속화는 RabbitMQ/Kafka에 위임

### 4.6 분석/감사 (역할 6)

별도 ADR로 상세 결정 완료:
- 집계 5종: ADR-0007
- 감사 로그: ADR-0008 → ADR-0009 (모듈 이전)
- 단일 진실의 원천: [`butterfly-analytics/docs/SCHEMA_DESIGN.md`](../../butterfly-analytics/docs/SCHEMA_DESIGN.md)

---

## 5. 스냅샷 컬럼 / 비정규화 컬럼 추가 기준 (한 페이지 가이드)

PR 리뷰 시 다음 의사결정 트리를 따른다.

```
새 컬럼 X를 테이블 T에 추가하려 한다.
│
├─ X가 다른 테이블 Y의 값을 복사한 것인가?
│   ├─ 아니오 → 일반 신규 컬럼. ADR 불필요.
│   └─ 예 → ↓
│
├─ Y의 값이 시간에 따라 변경 가능한가?
│   ├─ 아니오 (immutable) → 비정규화. 인덱스/조회 최적화 목적이라면 채택.
│   └─ 예 → ↓
│
├─ T가 이벤트/로그 계층(append-mostly)인가?
│   ├─ 예 → 스냅샷 채택. 발송/거래 시점의 사실을 고정.
│   └─ 아니오 → ↓
│
├─ T의 도메인 규칙상 Y가 변경되어도 T가 따라가야 하는가?
│   ├─ 예 → 비정규화 금지. JOIN으로 처리.
│   └─ 아니오 → 스냅샷 채택. 다만 갱신 책임자(애플리케이션 레이어)와 갱신 트리거를 ADR로 명문화.
```

### 5.1 채택할 때 반드시 명시할 4가지

새 비정규화/스냅샷 컬럼을 추가하는 PR은 다음을 PR 설명 또는 ADR에 기록한다:

1. **출처(source)** — 어느 테이블의 어느 컬럼을 복사했는가
2. **갱신 시점(write-time)** — INSERT 시 한 번만? 특정 이벤트 시 동기화?
3. **갱신 책임자(owner)** — 애플리케이션 레이어 / DB 트리거 / 배치
4. **드리프트 검증(reconciliation)** — 정합성 검증이 필요하다면 어떻게 (예: `send_reconciliations` 패턴)

### 5.2 자주 발생하는 안티패턴

- ❌ 단지 "JOIN을 줄이려고" 자주 변경되는 컬럼을 비정규화 (예: `users.name`을 모든 자식 테이블에 복사)
- ❌ 비정규화 컬럼만 갱신하고 출처 컬럼은 갱신 안 하는 트랜잭션
- ❌ 비정규화한 후 `updated_at`만 갱신하고 비정규화 값은 묵음으로 stale
- ❌ FK 없이 비정규화만 보유 (예: `campaign_id`만 두고 FK 생략) — 고아 row 생성 위험

---

## 6. 요구명세 ↔ 구현 매핑

요구명세 §12-1의 엔터티 목록과 현재 구현의 대응 관계.

| 요구명세 엔터티 | 구현 테이블/엔터티 | 비고 |
|---|---|---|
| Tenant | (현재 없음) | MVP는 단일 tenant. 향후 `tenants` 추가 시 모든 테이블에 `tenant_id` 추가 |
| Project | `projects` | |
| User (운영자) | `members` | 명세의 "User"는 운영자/관리자 |
| Customer | `users` | 명세의 "Customer" = 엔드유저 |
| Role | (auth 모듈 enum) | DB 테이블 없음, JWT/권한 코드로 처리 (ADR-0005) |
| Device | `devices` | |
| Event | `events` | |
| CustomerAttribute | `users` 컬럼 + `user_tags` | 별도 테이블 미도입 |
| Segment | `segments` | |
| SegmentCondition | `segments.condition` (JSON) | ADR-0001/0006 |
| Campaign | `campaigns` | |
| **CampaignMessage** | `notification_logs.message_template_id` (인라인) | §3.3 — 보류, 향후 도입 트리거 정의됨 |
| **Flow / FlowNode / FlowExecution** | (미구현) | §3.4 — MVP 스코프 외 |
| DeliveryJob / DeliveryLog | `notification_logs` | §4.5 — 흡수 |
| Template | `message_templates` | |
| ChannelCredential | (별도 모듈, 본 ADR 외) | 발송 도메인 |
| ConversionEvent | `user_action_logs` (`action_type='CONVERT'`) | 별도 테이블 미도입 |
| AuditLog | `audit_logs` | ADR-0008/0009 |

**잔존 갭**: `Tenant`, `ChannelCredential`은 본 ADR 범위 밖. 각 도메인 ADR 또는 후속 ADR에서 결정.

---

## Consequences

### 좋은 점

- 도메인 간 저장 전략 의사결정이 본 문서로 일관됨. 신규 PR은 §5의 의사결정 트리만 따르면 됨
- `CampaignMessage`/`Flow`/`FlowNode` 도입 보류가 명시적 결정으로 기록됨 — 향후 "왜 안 만들었나"가 사라지지 않음
- 분석/감사 도메인의 ADR-0007/0008/0009와 직교(orthogonal). 본 ADR은 횡단 원칙, 그쪽은 도메인 깊이

### 나쁜 점 / 주의

- 본 ADR이 "현황 스냅샷" 성격을 일부 가져 시간이 지나면 §4의 매핑 표가 stale될 수 있음 → 도메인 스키마 큰 변경 시 본 ADR §4를 함께 갱신할 책임을 PR 리뷰에서 강제(시즌 리뷰 시 점검)
- `Flow/FlowNode` 보류는 비즈니스 측의 자동화 플로우 요구가 들어오면 즉시 후속 ADR로 결정해야 함 — 발견 트리거를 §3.4에 명시
- `notification_logs`의 인라인 스냅샷이 늘어날수록 row 폭이 커짐. 현재 5개 컬럼 수준이며, 추가는 §5 의사결정 트리로 게이트

### Post-MVP 검토 항목

- `CampaignMessage` 도입 (A/B 테스트 정식화 시)
- `Flow`/`FlowNode` 도입 (자동화 엔진 정식 합류 시)
- `Tenant` 추가 (대행사형 멀티 계정 §13-4)
- `events`/`notification_logs` 월 단위 RANGE 파티셔닝

## More Information

- ERD (전체 DDL 통합): [`backend/docs/erd.md`](../erd.md)
- 분석 도메인 단일 진실 원천: [`butterfly-analytics/docs/SCHEMA_DESIGN.md`](../../butterfly-analytics/docs/SCHEMA_DESIGN.md)
- 분석 도메인 DDL: [`butterfly-analytics/db/schema_analytics_audit.sql`](../../butterfly-analytics/db/schema_analytics_audit.sql)
- 관련 ADR
  - [ADR-0001](0001-segment-condition-tree-json.md), [ADR-0006](0006-segment-condition-spec-v2.md) — 세그먼트 JSON 저장
  - [ADR-0002](0002-campaign-definition-execution-split.md) — 캠페인 정의/실행 분리
  - [ADR-0007](0007-analytics-aggregation-tables-v2.md) — 분석 집계 테이블 v2
  - [ADR-0008](0008-audit-log-architecture-v2.md), [ADR-0009](0009-audit-log-relocation-to-reporting.md) — 감사 로그
- 관련 Jira: `S14P31B101-47` (본 ADR로 종결), `S14P31B101-121` (DDL Flyway 적용)
