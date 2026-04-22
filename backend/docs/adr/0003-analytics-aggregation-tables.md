---
status: proposed
date: 2026-04-21
decision-makers: 역할 6 (분석/통합 검증)
consulted: 역할 5 (발송 워커), 역할 4 (캠페인)
---

# ADR-0003: 분석용 일자별 집계 테이블 도입

## Context and Problem Statement

분석 API 5개([06-analytics.md](../api/06-analytics.md)) 중 3개가 기간 필터(`?from=..&to=..`)를 요구한다:

- API 57 대시보드 요약 + `dailyTrend[]`
- API 59 캠페인 성과 비교 (기간)
- API 60 채널별 성과 비교 (기간)
- API 61 세그먼트별 성과 (기간)

초안 ERD의 `campaign_stats`는 **회차당 1행, 누적 총계**만 가진다.
"2026-04-01 ~ 04-20 구간" 같은 질의를 풀려면 **원천 `notification_logs`를 매번 스캔**해야 하는데,
이는 requirements 3-6 완료 기준(`운영 UI는 집계 테이블을 우선 조회하여 응답 속도를 보장한다`)을 정면 위반한다.

또한 requirements 11-6은 `campaign_metric_daily`, `channel_metric_daily`, `segment_metric_daily`를
필수 산출물로 명시했지만 ERD에는 통째로 빠져 있다.

## Decision Drivers

- 대시보드 응답 시간 수 초 이내 (requirements 3-6 / 4-1)
- 반복 발송(RECURRING) 회차별 집계가 가능해야 한다 ([ADR-0002](0002-campaign-definition-execution-split.md))
- 일자 버킷팅 기준 타임존이 명시되어야 한다 — MVP는 `Asia/Seoul` 서버 고정 (제안서 §2-4, 팀 합의 2026-04-21)
- Worker 재집계가 멱등해야 한다 (재처리 시 카운트가 두 배로 오르면 안 됨)
- 멀티테넌시 — 모든 집계 테이블은 `project_id`가 선두 인덱스여야 한다 (requirements 4-5)

## Considered Options

### Option A — 매 조회 시 `notification_logs` 실시간 집계

- 👍 테이블 추가 없음, 데이터 중복 없음
- 👎 `notification_logs`는 가장 큰 테이블 후보 — 서비스 성장 즉시 성능 붕괴
- 👎 requirements 3-6 완료 기준 위반
- 👎 JOIN(`notification_logs ⋈ campaigns ⋈ segments`) 비용이 매 요청마다 발생

### Option B — 일자별 사전 집계 테이블 (3종)

Worker가 발송/콜백 수신 시점에 해당 일자 버킷을 UPSERT 증분 갱신.
안전장치로 1시간 주기 재집계 배치가 `notification_logs`에서 원천 복구.

- 👍 조회 응답 시간 최소화
- 👍 RECURRING 회차별 집계 자연스러움 (`execution_id`를 PK에 포함)
- 👎 데이터 중복 — 원천과 집계 불일치 리스크 (재집계 배치로 완화)

### Option C — OLAP 엔진 도입 (ClickHouse, Druid 등)

- 👍 대규모 환경에서 이상적
- 👎 MVP 인프라/학습 비용 초과 — 제약 7-3 분산 복잡도 최소화 위반

## Decision Outcome

**선택: Option B (일자별 집계 테이블 3종)**.

### 스키마

```sql
-- 캠페인/회차별 일간 집계
CREATE TABLE `campaign_metric_daily` (
  `project_id`       BIGINT     NOT NULL,
  `campaign_id`      BIGINT     NOT NULL,
  `execution_id`     BIGINT     NOT NULL COMMENT '발송 회차 ID (RECURRING 대응)',
  `bucket_date`      DATE       NOT NULL COMMENT 'Asia/Seoul 기준 일자 (서버 JVM 타임존 고정)',
  `sent_count`       INT        NOT NULL DEFAULT 0,
  `delivered_count`  INT        NOT NULL DEFAULT 0,
  `clicked_count`    INT        NOT NULL DEFAULT 0,
  `failed_count`     INT        NOT NULL DEFAULT 0,
  `converted_count`  INT        NOT NULL DEFAULT 0,
  `updated_at`       DATETIME   NOT NULL,
  PRIMARY KEY (`project_id`, `campaign_id`, `execution_id`, `bucket_date`),
  INDEX `idx_cmd_project_campaign_date` (`project_id`, `campaign_id`, `bucket_date`)
);

-- 채널별 일간 집계
CREATE TABLE `channel_metric_daily` (
  `project_id`       BIGINT                 NOT NULL,
  `channel`          ENUM('PUSH','EMAIL')   NOT NULL,
  `bucket_date`      DATE                   NOT NULL,
  `sent_count`       INT                    NOT NULL DEFAULT 0,
  `delivered_count`  INT                    NOT NULL DEFAULT 0,
  `clicked_count`    INT                    NOT NULL DEFAULT 0,
  `failed_count`     INT                    NOT NULL DEFAULT 0,
  `updated_at`       DATETIME               NOT NULL,
  PRIMARY KEY (`project_id`, `channel`, `bucket_date`)
);

-- 세그먼트별 일간 집계
CREATE TABLE `segment_metric_daily` (
  `project_id`       BIGINT    NOT NULL,
  `segment_id`       BIGINT    NOT NULL,
  `bucket_date`      DATE      NOT NULL,
  `campaign_count`   INT       NOT NULL DEFAULT 0,
  `sent_count`       INT       NOT NULL DEFAULT 0,
  `delivered_count`  INT       NOT NULL DEFAULT 0,
  `clicked_count`    INT       NOT NULL DEFAULT 0,
  `failed_count`     INT       NOT NULL DEFAULT 0,
  `updated_at`       DATETIME  NOT NULL,
  PRIMARY KEY (`project_id`, `segment_id`, `bucket_date`)
);
```

### 갱신 전략

1. **실시간 증분** — Worker가 발송/콜백 이벤트 처리 시 해당 버킷에 원자적 `INSERT ... ON DUPLICATE KEY UPDATE` (MySQL upsert)
2. **재집계 배치** — 1시간 주기로 `notification_logs`에서 지난 25시간 구간을 완전 재집계하여 실시간 증분 누락을 복구 (멱등)
3. **타임존** — `notification_logs.sent_at`(UTC 저장)을 `Asia/Seoul`로 변환해 `DATE()`로 버킷팅. MVP 한국 한정이라 프로젝트별 타임존 컬럼은 도입하지 않는다 (제안서 §2-4 팀 합의). 운영 환경 JVM은 `-Duser.timezone=Asia/Seoul`로 고정.

### 카운트 규칙 (제안서 §1-3)

`notification_logs.status` enum은 전이식(`SENT → DELIVERED → CLICKED`)이므로 **상태 열 카운트를 쓰지 않고 타임스탬프 기준** 집계:

- `sent_count = COUNT(*) WHERE sent_at IS NOT NULL`
- `delivered_count = COUNT(*) WHERE delivered_at IS NOT NULL`
- `clicked_count = COUNT(*) WHERE clicked_at IS NOT NULL`
- `failed_count = COUNT(*) WHERE status = 'FAILED'`

비율:

- `deliveryRate = delivered_count / sent_count * 100`
- `clickRate = clicked_count / delivered_count * 100` — 분모는 delivered (업계 관행)

### 의존 항목 (합의 필요)

본 ADR은 다음 결정에 의존한다:

- `notification_logs.project_id` 컬럼 추가 + 인덱스 (제안서 §1-2, 역할 5 합의 필요) — **Blocker**
- 발송 카운트 규칙 (제안서 §1-3, 역할 5) — **Blocker**
- ~~프로젝트 타임존 컬럼~~ (제안서 §2-4) — ✅ 2026-04-21 팀 합의: `Asia/Seoul` 서버 고정으로 대체 (컬럼 미도입)
- 캠페인 정의/실행 분리 ([ADR-0002](0002-campaign-definition-execution-split.md)) — ✅ 확정

## Consequences

### 좋은 점

- 대시보드/성과 비교 API가 집계 테이블 단일 조회로 응답 (requirements 3-6 완료 기준 충족)
- RECURRING 회차별 드릴다운 지원
- `project_id` 선두 인덱스로 멀티테넌시 격리가 DB 레벨에서 강제됨

### 나쁜 점 / 주의

- 원천-집계 이중 저장 → **재집계 배치가 반드시 멱등**해야 한다
- 재처리 시 `updated_at`이 갱신되어 "데이터 기준 시각"으로 혼동 가능 → API 응답에 `metricsComputedAt` 필드 별도 제공
- `converted_count`는 전환 이벤트 스코프 결정(제안서 §2-3) 이후 활성화
- 파티셔닝은 MVP 범위 밖 — 단, `bucket_date` 기준 월 단위 파티셔닝이 자연스러우므로 PK 설계가 이를 방해하지 않도록 유지

## More Information

- 제안서 §1-1: [backend/docs/proposals/01-erd-api-review.md](../proposals/01-erd-api-review.md)
- 관련 ADR: [ADR-0002 캠페인 정의/실행 분리](0002-campaign-definition-execution-split.md)
- requirements 3-6, 11-6
- Analytics API: [backend/docs/api/06-analytics.md](../api/06-analytics.md)
