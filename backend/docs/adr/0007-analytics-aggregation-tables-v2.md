---
status: accepted
date: 2026-04-28
decision-makers: 역할 6 (분석/통합 검증)
consulted: 역할 4 (캠페인), 역할 5 (발송 워커), 역할 1 (인증/권한)
supersedes: ADR-0003
---

# ADR-0007: 분석 집계 테이블 v2 — 회차·템플릿·실패·전사 집계 분리

## Context and Problem Statement

[ADR-0003](0003-analytics-aggregation-tables.md)는 v1 단계에서 캠페인/채널/세그먼트 3종 일자별 집계 테이블을 도입했다. 그러나 이후 다음 4가지 변화가 발생하여 스키마와 카운트 규칙을 전면 갱신할 필요가 생겼다:

1. **반복 캠페인 회차 분리** ([ADR-0002](0002-campaign-definition-execution-split.md)) — `campaign_executions` 테이블이 별도 도입되어 회차별 추적이 필수
2. **`notification_logs.status` 의 `CLICKED` 제거** — 사용자 반응(오픈/클릭/전환)은 별도 `user_action_logs` 로 분리 (1:N 관계)
3. **분석 요구 확장** — 템플릿 효율(어떤 제목이 효과적인가), 실패 원인 분석(에러 코드 TOP N), 프로젝트 전사 대시보드(활성 사용자/전체 발송)가 신규 요구사항으로 추가됨
4. **실시간 + 배치 이중 갱신 채택** — hourly 5분 스트림 + daily 새벽 배치의 2단 구조가 합의됨

ADR-0003의 3종 테이블만으로는 이 요구를 충족할 수 없다.

## Decision Drivers

- 회차(execution) 단위 드릴다운이 필요하지만, 집계 테이블이 회차×채널×시간 차원으로 폭증하면 안 된다
- 사용자 반응을 status가 아닌 `user_action_logs`에서 카운트해야 한다 (CLICKED 제거)
- 템플릿/실패/전사 집계는 캠페인 단위와 직교(orthogonal)하므로 별도 테이블로 분리
- hourly 실시간 대시보드와 daily 리포트 모두 필요
- 모든 쓰기는 자연 키 UPSERT로 멱등성 보장
- 멀티테넌시 — 모든 집계 테이블이 `project_id` 컬럼을 직접 보유 (비정규화)

## Considered Options

### Option A — ADR-0003 그대로 유지 + 컬럼만 추가

- 👍 변경 최소화
- 👎 회차 단위 분석 불가 (`campaign_metric_daily.execution_id`를 PK에 넣으면 카디널리티 폭증)
- 👎 템플릿/실패/전사 집계가 들어갈 자리 없음
- 👎 status 기반 카운트 규칙이 v2 스키마와 충돌

### Option B — 5종 집계 테이블 + hourly 추가 (채택)

- `campaign_stats_hourly` — 5분 스트림, 시간 단위 (실시간 대시보드)
- `campaign_stats_daily` — 매일 새벽 롤업, 비율 지표 포함
- `template_stats_daily` — 템플릿 효율 분석
- `failure_stats_daily` — 실패 원인 분석
- `project_stats_daily` — 프로젝트 전사 집계 (대시보드 진입 화면)

회차 추적은 집계 테이블 PK가 아닌 **`notification_logs.campaign_execution_id`** 로 수행하고, 회차별 상세 화면은 원장 + `campaign_stats_hourly` 윈도우 조회로 구성한다.

- 👍 분석 요구 4가지 모두 커버
- 👍 회차 폭증 위험 회피
- 👎 테이블 5종으로 늘어 운영 복잡도 증가 (파티셔닝/보관 정책 일관성 필요)

### Option C — 통합 `analytics_metrics` 테이블 + 차원 컬럼

EAV(Entity-Attribute-Value) 형태로 모든 집계를 단일 테이블에

- 👍 스키마 변경 부담 적음
- 👎 인덱스 설계가 거의 불가능, 조회 성능 붕괴
- 👎 SQL 가독성/디버깅성 최악

## Decision Outcome

**선택: Option B (5종 집계 테이블)**.

DDL 단일 진실의 원천은 [`backend/butterfly-analytics/db/schema_analytics_audit.sql`](../../butterfly-analytics/db/schema_analytics_audit.sql) v2.0이며, 본 ADR은 그 결정 근거를 기록한다.

### 테이블 5종 요약

| 테이블 | 자연 키 (UNIQUE) | 갱신 주기 | 용도 |
|---|---|---|---|
| `campaign_stats_hourly` | `(campaign_id, channel, bucket_hour)` | 5분 스트림 UPSERT | 실시간 대시보드, 시간대별 추이 |
| `campaign_stats_daily` | `(campaign_id, channel, bucket_date)` | 새벽 1시 배치 (hourly 롤업) | 캠페인 성과 리포트, 비율 지표 |
| `template_stats_daily` | `(message_template_id, channel, bucket_date)` | 새벽 1시 배치 | 템플릿 A/B 비교, 가장 효과적인 제목 |
| `failure_stats_daily` | `(project_id, channel, error_code, bucket_date)` | 새벽 1시 배치 | 실패 원인 TOP N 대시보드 |
| `project_stats_daily` | `(project_id, bucket_date)` | 새벽 1시 배치 | 프로젝트 전사 대시보드 |

### 카운트 규칙 (v2)

`notification_logs.status`에서 `CLICKED`가 제거되었으므로 사용자 반응은 `user_action_logs.action_type` 기준으로 카운트한다:

- `sent_count = COUNT(notification_logs WHERE status IN ('SENT','DELIVERED'))`
- `delivered_count = COUNT(notification_logs WHERE status = 'DELIVERED')`
- `failed_count = COUNT(notification_logs WHERE status = 'FAILED')`
- `open_count = COUNT(user_action_logs WHERE action_type = 'OPEN')`
- `click_count = COUNT(user_action_logs WHERE action_type = 'CLICK')`
- `convert_count = COUNT(user_action_logs WHERE action_type = 'CONVERT')`

### 비율 지표 공식 (KPI 정의서 `S14P31B101-76` 기준)

```
delivery_rate = delivered_count / sent_count    * 100
open_rate     = open_count      / delivered_count * 100
click_rate    = click_count     / open_count    * 100
convert_rate  = convert_count   / sent_count    * 100
```

**분모가 0이면 NULL 반환** (응답에서 0%로 표시하면 의미가 왜곡됨).

ADR-0003의 `clickRate = clicked / delivered` 와 달라진 핵심: **클릭의 분모를 delivered → open으로 변경**. 이메일 업계는 delivered를 분모로 쓰지만, 푸시는 "오픈한 사용자 중 몇 %가 클릭했는지"가 의미가 더 명확하다는 합의에 따른다.

### 회차(execution) 추적 위치

- 집계 테이블 PK에는 `campaign_execution_id`를 **포함하지 않는다** (카디널리티 폭증 방지)
- 회차별 성과는 `notification_logs.campaign_execution_id` + `campaign_stats_hourly` 윈도우 조회로 동적 계산
- 회차 단위 API(`/api/v1/campaign-executions/{executionId}/stats`)는 hourly에서 해당 회차의 sendStartedAt~sendCompletedAt 윈도우를 추출

### 갱신 전략 (2단 구조)

1. **Hourly 스트림 워커** (5분 주기)
   - 직전 5분간의 `notification_logs` + `user_action_logs`를 `campaign_stats_hourly`에 UPSERT
   - `INSERT ... ON DUPLICATE KEY UPDATE sent_count = sent_count + VALUES(sent_count), ...`
   - lag 누적 방지를 위해 5분 안에 처리 완료 SLA

2. **Daily 롤업 배치** (매일 새벽 1시)
   - hourly 데이터를 daily 4종 테이블로 롤업
   - 비율 지표(open_rate 등)는 daily 단계에서 계산 (분모 0 → NULL)
   - 1일 전체 단위 멱등 재실행 가능 (`force=true` 시 기존 row 삭제 후 재집계)

### 멀티테넌시

모든 집계 테이블에 `project_id` 컬럼이 직접 존재하며, 보조 인덱스의 선두 또는 자연 키에 포함된다. 이는 ADR-0003에서 잡았던 원칙을 그대로 계승.

### 타임존

`Asia/Seoul` 서버 고정. JVM 옵션 `-Duser.timezone=Asia/Seoul`. ADR-0003에서 합의된 정책 그대로.

## Consequences

### 좋은 점

- 분석 4대 요구(캠페인/템플릿/실패/전사) 모두 단일 호출로 응답
- 회차 카디널리티 폭증 회피 — 회차 추적은 원장 + hourly 윈도우 조회로 해결
- hourly + daily 2단 구조로 실시간성과 정확성 모두 확보
- v2 스키마와 카운트 규칙 일치 — `CLICKED` status 제거 영향 흡수

### 나쁜 점 / 주의

- 테이블 5종 운영 부담 — 파티셔닝/보관 정책을 일관되게 가져가야 함 (월 단위 RANGE 파티셔닝 후보)
- hourly 워커 SLA(5분) 미준수 시 lag 누적 — `system_health_snapshots`에 메트릭 기록으로 모니터링
- daily 롤업 실패 시 부분 갱신 위험 — `force=true` + bucket_date 단위 재실행 절차 운영 매뉴얼화 필요
- 비율 지표 분모 변경(`click_rate` 분모: delivered → open)으로 v1 리포트와 수치 비교 시 주의

### Post-MVP 개선 포인트

- ClickHouse 등 OLAP 엔진으로 hourly 워커 대체 (대용량 시)
- attribution window 가변화 (현재는 `actionAt` 그대로 카운트, 7일 윈도우 등 도입 가능)
- 회차 단위 집계 전용 테이블 도입 (지금은 회차 카디널리티 위험 회피로 미도입)

## More Information

- 단일 진실의 원천: [`backend/butterfly-analytics/docs/SCHEMA_DESIGN.md`](../../butterfly-analytics/docs/SCHEMA_DESIGN.md)
- DDL: [`backend/butterfly-analytics/db/schema_analytics_audit.sql`](../../butterfly-analytics/db/schema_analytics_audit.sql)
- API 명세: [`backend/butterfly-analytics/docs/butterfly_api_spec_v2.md`](../../butterfly-analytics/docs/butterfly_api_spec_v2.md)
- 관련 ADR: [ADR-0002](0002-campaign-definition-execution-split.md), [ADR-0008](0008-audit-log-architecture-v2.md)
- 슈퍼시드: [ADR-0003](0003-analytics-aggregation-tables.md)
- 관련 Jira: `S14P31B101-121` (DDL 적용), `S14P31B101-124` (집계 배치 구현), `S14P31B101-76` (KPI 정의서)
