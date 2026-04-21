---
status: accepted
date: 2026-04-21
decision-makers: 역할 4 (캠페인/템플릿/채널 설정)
consulted: 역할 6 (분석/통합 검증), 역할 5 (발송 워커)
---

# ADR-0002: 캠페인 정의와 실행 회차 분리

## Context and Problem Statement

초안 ERD의 `campaigns` 테이블은 다음을 하나에 몰아 담았다:

- 캠페인 정의(이름, 세그먼트, 채널, 제목/본문/이미지)
- 발송 스케줄(`scheduled_at`, `recurring_cron`, `next_execution_at`)
- 실행 상태(`status ENUM(DRAFT, SCHEDULED, SENDING, COMPLETED, FAILED)`)
- 실행 이력(`send_started_at`, `send_completed_at`)

이 구조는 세 가지 요구사항을 충족하지 못한다:

1. **반복 발송 (RECURRING)** — cron으로 매일 나가는 캠페인이 하나의 row라면 "어제 발송은 성공, 오늘은 실패" 같은 회차별 이력이 저장 불가
2. **템플릿 변경 후 과거 재현** (requirements 3-4 완료 기준) — 제목/본문이 `campaigns` 안에만 있으면 수정 시 과거 발송 이력이 왜곡됨
3. **취소 상태의 모호성** — 제안서 §2-1에서 지적: `SCHEDULED → DRAFT (cancel)` 전이는 "취소된 것"과 "새로 만들어 저장만 한 것"을 구분 불가하게 만든다
4. **중복 발송 방지** — Kafka 네트워크 재시도로 동일 발송이 두 번 집행될 위험. `campaigns.version`(낙관적 락)만으로는 발송 자체의 멱등성을 보장하지 못함

또한 감사 로그 대상인 **채널 자격 증명**(FCM 등)도 `projects.fcm_credentials` 컬럼에 박혀 있어 감사 추적이 어려웠다.

## Decision Drivers

- RECURRING 캠페인의 회차별 성과 집계가 가능해야 한다 (분석 API 58/59 요구)
- 템플릿 수정이 과거 발송 이력에 영향을 주어서는 안 된다
- 네트워크 재시도에 의한 중복 발송이 원천 차단되어야 한다 (requirements 4-4 안정성)
- 채널 자격증명 변경이 감사 로그(`audit_logs`)로 추적 가능해야 한다 (00-common 감사 로그 대상)
- MVP 일정 내 구현 가능해야 한다

## Considered Options

### Option A — `campaigns` 단일 테이블 유지 + `CANCELED` 상태만 추가

제안서 §2-1 원안. `campaigns.status`에 `CANCELED` 값만 더하고, 템플릿/자격증명은 그대로 둔다.

- 👍 변경 범위가 가장 작다
- 👎 RECURRING 회차별 집계 불가 — 근본 문제 해결 안됨
- 👎 템플릿 변경 → 과거 이력 왜곡 여전
- 👎 중복 발송 방지 수단 없음

### Option B — 정의/실행 분리 + `templates`/`channel_credentials` 신설

`campaigns`는 정의만, `campaign_executions`가 실제 발송 단위. 템플릿·자격증명은 별도 테이블.

- 👍 RECURRING 회차별 집계 자연스럽게 가능
- 👍 발송 시점 스냅샷 저장으로 과거 재현 100% 보장
- 👍 `idempotency_key UNIQUE` 제약으로 중복 발송 원천 차단
- 👍 `channel_credentials` 분리로 감사 로그 연동 깔끔
- 👎 테이블 4개가 신설되어 초기 구현량 증가
- 👎 분석 도메인의 집계 테이블 PK와 API가 `execution_id` 기반으로 재설계 필요

### Option C — 이벤트 소싱

모든 캠페인 상태 변화를 이벤트 스트림으로 저장.

- 👍 이론적으로 가장 유연
- 👎 MVP 일정/역량 초과 — 제약 7-3 "분산 시스템 복잡도 최소화" 위반

## Decision Outcome

**선택: Option B**.

### 새 스키마 구조

#### `campaigns` — 캠페인 정의 (운영 생애주기만 표현)

```sql
campaign_id    BIGINT PK
project_id     BIGINT FK
member_id      BIGINT        -- 생성자
segment_id     BIGINT NULL   -- NULL = 전체 발송
template_id    BIGINT FK     -- 참조
name           VARCHAR(255)
channel        ENUM('PUSH','EMAIL')
type           ENUM('IMMEDIATE','SCHEDULED','RECURRING')
status         ENUM('DRAFT','ACTIVE','PAUSED','ARCHIVED')  -- 운영 상태
recurring_cron VARCHAR(50)
version        BIGINT        -- 낙관적 락
created_at, updated_at
```

상태 의미:

- `DRAFT` — 필수 설정 미완료 or 임시 저장
- `ACTIVE` — 운영 중 (즉시 발송 후 또는 스케줄러가 감시 중)
- `PAUSED` — RECURRING을 운영자가 임시 중지
- `ARCHIVED` — 종료 or 리스트 숨김

#### `campaign_executions` — 실제 발송 회차 (워커가 처리하는 단위)

```sql
execution_id      BIGINT PK
campaign_id       BIGINT FK
status            ENUM('SCHEDULED','SENDING','COMPLETED','FAILED','CANCELED')
snapshot_title    VARCHAR(255)   -- 발송 확정 시점 변수 치환 결과
snapshot_body     TEXT
snapshot_image_url VARCHAR(500)
snapshot_data     JSON
total_target      INT NULL       -- SCHEDULED: NULL, SENDING 진입 시 세그먼트 엔진 호출 후 UPDATE
scheduled_at      DATETIME
send_started_at   DATETIME NULL
send_completed_at DATETIME NULL
idempotency_key   VARCHAR(100) UNIQUE
created_at
```

`idempotency_key` 규칙:

- **IMMEDIATE**: 클라이언트가 HTTP 헤더 `Idempotency-Key: <UUID>` 로 전달 → 그대로 저장. 사용자가 "발송" 버튼을 연속 클릭해도 UNIQUE 제약이 튕겨냄
- **SCHEDULED / RECURRING**: 스케줄러가 `camp_{campaignId}_tick_{yyyyMMddHHmm}` 형태로 생성

`total_target` 규칙:

- 예약 시점에는 미래 세그먼트를 알 수 없으므로 NULL
- `SENDING` 상태 진입 직전에 세그먼트 엔진(역할 3)을 호출하여 실모수 계산 → `total_target`에 UPDATE 후 발송 시작

#### `templates` (신설)

```sql
template_id    BIGINT PK
project_id     BIGINT FK
channel        ENUM('PUSH')
name           VARCHAR(255)
title_template TEXT
body_template  TEXT
image_url      VARCHAR(500) NULL
created_at, updated_at
```

#### `campaign_stats` PK 변경

```sql
PRIMARY KEY (execution_id)   -- 기존: (campaign_id)
```

#### `channel_credentials` (신설)

```sql
credential_id    BIGINT PK
project_id       BIGINT FK
channel          ENUM('PUSH','EMAIL')
encrypted_secret VARCHAR(500)
is_active        BOOLEAN
created_at, updated_at
UNIQUE (project_id, channel)
```

- 감사 이력은 `audit_logs`(역할 1)에 위임
- 워커(역할 5)는 `is_active=false` 확인 시 해당 execution을 `FAILED(Credentials Inactive)`로 종결하고 오류 기록
- MVP는 채널당 1개(`UNIQUE`). 추후 iOS/Android 분리가 필요해지면 `PUSH_IOS`, `PUSH_AOS`로 enum을 쪼개는 방식을 우선 검토한다 (다중 row 허용보다 안전)

### 분석 도메인 파급 효과

- **API 58 `/campaigns/{campaignId}/stats`** = 모든 회차 합산 (누적)
- **신규 API** `GET /api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats` — 회차별 상세
- **집계 테이블** (ADR-0003)의 PK에 `execution_id` 포함
- **"기간 내 발송된 캠페인 수"** 는 `campaign_executions.send_started_at` 기준 카운트 (campaigns.status 참조 금지)

## Consequences

### 좋은 점

- RECURRING 캠페인의 회차별 비교·드릴다운이 자연스럽다
- 템플릿 변경이 과거 발송 이력을 절대 왜곡하지 않는다 (snapshot 보유)
- `idempotency_key`로 중복 발송 원천 차단
- `channel_credentials` 분리로 감사 로그 대상이 깔끔 (00-common 요건 충족)
- 캠페인 "운영 상태"와 "발송 진행 상태"가 분리되어 UI/API 설계가 명확

### 나쁜 점 / 주의

- 신규 테이블 4개 (`campaign_executions`, `templates`, `channel_credentials`, 변경된 `campaign_stats`) → MVP 초기 구현량 증가
- 역할 6은 **모든 분석 API와 집계 테이블 PK를 `execution_id` 기반으로 재설계** 필요 (ADR-0003)
- 역할 5는 워커 로직을 `campaign_executions` 기준으로 작성
- 기존 [backend/docs/api/04-campaign.md](../api/04-campaign.md) 상태 흐름표는 `campaign_executions.status` 기준으로 다시 써야 한다

## More Information

- 관련 ADR: [ADR-0003 분석 집계 테이블](0003-analytics-aggregation-tables.md)
- 제안서: [backend/docs/proposals/01-erd-api-review.md §2-1](../proposals/01-erd-api-review.md)
- requirements 3-4 / 3-5 / 4-4
- 감사 로그 대상: [backend/docs/api/00-common.md](../api/00-common.md)
