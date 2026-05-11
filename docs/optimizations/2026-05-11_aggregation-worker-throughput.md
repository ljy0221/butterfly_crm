---
date: 2026-05-11
slug: aggregation-worker-throughput
category: PERF
tags: [#performance-axis, #async-axis, #aggregation, #hourly, #daily, #upsert, #k6]
related-jira: S14P31B101-68
related-adr: ADR-0007
related-branch: Docs/S14P31B101-68_Lt03ThroughputCard
git-before: 99568b1 (origin/develop, "Merge branch 'Feat/worker-campaign-send-pipeline'")
git-after: TBD (5/14 QA Run #2 시점 측정 commit)
agent-source: user (5/11 LT-03 박제 지시), optimization-historian (본 카드 양식)
status: BEFORE-ONLY
before-measured: TBD (5/14 QA Run #2 — hourly aggregator + daily rollup 둘 다)
after-measured: TBD
---

# 분석 집계 워커 처리량 — 30K row hourly UPSERT + daily 롤업 latency

## 한 줄 요약

**`HourlyAggregator`(124-A 머지본) + `DailyRollupService`(191 머지본) 가 정상 운영 트래픽(1시간 30K notification_logs row × 캠페인 5 × 채널 2) 을 처리할 때의 처리량·latency·메모리를 5/14 QA Run #2 에서 1차 실측한다. 본 카드는 측정 전 baseline 박제용 BEFORE-ONLY 카드 — 측정 후 COMPLETE 승격하고, 5/22 발표 "비동기 + 성과 측정 축" 의 핵심 증거로 활용한다.**

> 측정 대상 흐름: notification_logs → aggregateHourly(SQL GROUP BY) → upsert(campaign_stats_hourly) → DailyRollupService.rollup(SQL 합산) → campaign_stats_daily / template_stats_daily / failure_stats_daily / project_stats_daily

## Situation (배경)

### ADR-0007 명세

ADR-0007(분석 집계 테이블 v2) 의 정책:
- hourly: 캠페인×채널×bucket_hour 자연 키 UPSERT, 멱등성 보장
- daily: hourly 합산 기반 + open/click/convert 정확 계산
- 비율 지표: KPI 정의서 §3 공식 (delivery_rate, open_rate, click_rate, convert_rate)

운영 데모 시 대시보드는 **집계 테이블만 조회** (집계 테이블 우선 정책). 따라서 hourly 워커가 5분 윈도우 안에 끝나야 대시보드 latency 가 보장되고, daily 롤업이 매일 02:00 KST 이전에 끝나야 정합성 검증 배치 시작 시점을 지킨다.

### 시스템 구성 (Before baseline)

| 컴포넌트 | 구성 | 머지본 |
|---|---|---|
| Hourly 워커 | `@Scheduled` 5분 주기, `HourlyAggregator.aggregate(bucketHour, force)` | S14P31B101-124 (124-A) |
| Hourly Repository | `ReportingNotificationLogRepository.aggregateHourly(from, to)` GROUP BY 쿼리 | 124-A |
| Hourly UPSERT | `CampaignStatsHourlyRepository.upsert(...)` native MERGE | 124-A |
| Daily 롤업 서비스 | `DailyRollupService` cron(`0 0 1 * * *`) — hourly 합산 + 비율 SQL 계산 | S14P31B101-191 (Refactor 머지본 e3211d9 / f0b08df) |
| Daily 4종 테이블 | `campaign_stats_daily`, `template_stats_daily`, `failure_stats_daily`, `project_stats_daily` | 191 |
| DB | MySQL 8.x, 자연 키 UNIQUE 인덱스 (UPSERT 멱등) | V2 마이그레이션 |

### Code Freeze 정합성

- D-4 (5/15 Code Freeze)
- DoD: 발표 자료 "비동기 처리" + "성과 측정" 두 축의 근거 → 본 카드가 핵심 증거
- 5/22 발표 D-11

## Task (목표)

1. **Before baseline 박제** — 시드 데이터 규모, 측정 환경, 코드 commit, 예상값 정착
2. **5/14 QA Run #2 에서 LT-03 실측** ([backend/docs/qa/03-load-and-failure-scenarios.md §2.3](../../backend/docs/qa/03-load-and-failure-scenarios.md#23-시나리오-lt-03--분석-집계-워커-부하))
3. **결과 카드 승격** — Result 섹션 채움, status BEFORE-ONLY → COMPLETE
4. **병목 식별 시** — 인덱스 추가 / 쿼리 재설계 등 최적화 PR → 별도 카드 분리 (Before/After 1쌍)

## Action (조치)

### Before 증거 — 측정 전 상태 박제

#### 코드 commit 기준

- `git rev-parse origin/develop` = **`99568b1`** (2026-05-11 본 카드 작성 시점)
- HourlyAggregator: 124-A 머지본 (`backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/aggregation/service/HourlyAggregator.java`)
- DailyRollupService: 191 Refactor 머지본 (`backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/aggregation/service/DailyRollupService.java`)
- 쿼리: `ReportingNotificationLogRepository.aggregateHourly` / `aggregateTemplateDaily` / `aggregateFailureDaily`

#### 측정 도구·환경

| 항목 | 값 (예정) | 비고 |
|---|---|---|
| 환경 | dev (TestContainers MySQL 또는 docker-compose.dev.yml) | 격리된 MySQL |
| 시드 규모 | 30K notification_logs row, 1시간치 (캠페인 5 × 채널 2 × 시간 균등 분포) | `seed-aggregation-test.sql` (별도 PR) |
| 추가 시드 | 사용자 반응 이벤트 (user_action_logs) 1000건 — open/click/convert 분포 | KPI 비율 계산 검증용 |
| 측정 도구 | JMH (또는 단순 Spring Boot 통합 테스트 + System.currentTimeMillis) | 한 회 측정으로 충분 |
| 트리거 | `POST /internal/stats/aggregate/hourly?bucketHour=<since>&force=true` | HourlyAggregatorController (`@RequestMapping("/internal/stats/aggregate/hourly")`) |
| 트리거 (daily) | `POST /internal/stats/rollup/daily?force=true` | DailyRollupController |

#### 예상값 (가설)

| 지표 | 예상값 | 근거 |
|---|---|---|
| `aggregateHourly` 단일 호출 시간 | 1~3초 | 30K row GROUP BY (campaign × channel), 인덱스 idx_notification_logs_project_time |
| `CampaignStatsHourlyRepository.upsert` × 10건 | 0.2~0.5초 | 10건(5×2) native MERGE |
| hourly aggregator 총 시간 (force=true) | **2~4초** | 위 합산, < 5초 임계치 |
| 멱등 재호출 시간 | 2~4초 | 같은 윈도우 재집계 — 측정값 같아야 함 |
| daily 롤업 (4종 테이블) | **3~6초** | 비율 SQL 계산 + 4종 UPSERT |
| MySQL connections peak | 3~5 | HikariCP 기본 풀, 한도 20 미만 |
| JVM heap peak | 256MB 미만 | 30K row 스트림 처리 |

**예상이 빗나가는 케이스**:
- hourly > 10초 — 의심 1순위: `aggregateHourly` GROUP BY 에 인덱스 미적용 (project_id, created_at 복합 인덱스 확인)
- daily > 30초 — 의심 2순위: `aggregateTemplateDaily` / `aggregateFailureDaily` 가 daily 집계 테이블 대신 notification_logs 원장을 직접 조회 (191 Refactor 후 hourly 합산 방식인지 재확인)
- 멱등 재호출 시간 ≠ 첫 호출 시간 — UPSERT 멱등성 또는 windowEnd 동작 결함

### 적용된 변경 (Before 시점)

본 카드는 **측정 전 baseline 박제**이므로 코드 변경 없음. 단, 박제를 가능하게 한 머지 이력:

| 머지 | 의미 |
|---|---|
| `ee283c3` (191 초기) | DailyAggregator + POST /internal/stats/rollup/daily |
| `e3211d9` (191 Refactor) | DailyAggregator → DailyRollupService 재설계 — hourly 합산 방식 + SQL 비율 계산 |
| `c18e10e` (191 controller 교체) | DailyRollupController 신규 |
| 124-A | HourlyAggregator + campaign_stats_hourly UPSERT |

## Result (성과)

> ⏳ **측정 대기 중** — 5/14 QA Run #2 에서 채움. 양식:

### 정량 비교 표 (TBD)

| 지표 | Before (예상) | Before (실측) | 임계치 | 판정 |
|---|---|---|---|---|
| hourly 단일 집계 시간 | 2~4초 |   | < 5초 (LT-03 임계) |   |
| hourly 멱등 재호출 시간 | 2~4초 (동일) |   | 같아야 함 |   |
| daily 롤업 총 시간 | 3~6초 |   | < 30초 (운영 임계) |   |
| campaign_stats_hourly row 수 | 5×2=10 (campaign × channel) |   | 정확 일치 |   |
| 4종 daily row 수 | 캠페인별·템플릿별·실패별·프로젝트별 |   | 정확 일치 |   |
| MySQL connections peak |   |   | < 10 |   |
| JVM heap peak |   |   | < 256MB |   |

### 멱등성 검증 (TBD)

- 같은 bucketHour 로 hourly 3회 호출 → row count 변화 없음 (10건 유지)
- daily `force=true` 호출 → 기존 row 삭제 후 동일 결과 (멱등)

### KPI 비율 검증 (TBD)

KPI 정의서 §3 공식 vs 실제 daily 테이블의 비율 값:
- `delivery_rate = delivered / sent × 100` — 분모 0 → null 확인
- `open_rate = open / delivered × 100`
- `click_rate = click / open × 100`
- `convert_rate = convert / sent × 100`

### 병목 식별 (있을 때만, TBD)

- EXPLAIN 출력 → `_evidence/2026-05-14_aggregation-hourly-explain.txt`
- 식별 시 후속 최적화 PR → 별도 카드로 Before/After 1쌍 박제

## Reflection (회고)

> ⏳ 측정 후 채움. 예상값 vs 실측값 정확도 자체가 학습 자료.

## References

### 시나리오 정의
- [backend/docs/qa/03-load-and-failure-scenarios.md §2.3](../../backend/docs/qa/03-load-and-failure-scenarios.md) — LT-03

### ADR
- [ADR-0007 분석 집계 테이블 v2](../../backend/docs/adr/0007-analytics-aggregation-tables-v2.md) — 자연 키 UPSERT + hourly 합산 정책
- [KPI 정의서](../../backend/docs/operations/kpi-and-attribution.md) — 비율 공식

### 관련 Jira
- `S14P31B101-68` — 부하 테스트 시나리오 (LT-03 출처)
- `S14P31B101-124` (124-A) — HourlyAggregator 정착
- `S14P31B101-191` — daily 롤업 (이미 Refactor 머지 완료, 본 카드는 그 위에서 측정)

### 코드
- `backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/aggregation/service/HourlyAggregator.java`
- `backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/aggregation/service/DailyRollupService.java`
- `backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/notification/repository/ReportingNotificationLogRepository.java`

### 증거 보존 (`docs/optimizations/_evidence/`, TBD)
- `2026-05-14_aggregation-hourly-explain.txt` — DB EXPLAIN
- `2026-05-14_aggregation-daily-explain.txt` — daily 4종 쿼리 EXPLAIN
- `2026-05-14_aggregation-timing.json` — 단계별 wall-clock 박제

## 포트폴리오 변환 (5/22 발표·이력서·블로그·STAR)

### 4축 매핑

- **비동기** ⭐ — Spring `@Scheduled` 5분/일 주기, MERGE 멱등, force 재집계
- **성과 측정** ⭐ — 집계 테이블 우선 정책으로 운영 대시보드 latency 보장 (실측값으로 입증)
- **멀티테넌트** — `project_stats_daily` 가 프로젝트 단위 집계 (자연 키에 project_id 포함 확인)
- **채널 추상화** — 직접 영향 없음 (집계는 channel enum 단위로 GROUP BY)

### 발표 슬라이드 헤드라인 후보 (실측 후 확정)

- "**30K row 1시간치 hourly 집계 3.2초** — 5분 윈도우 대비 16배 마진"
- "**daily 4종 롤업 5.8초** — 02:00 cron 직전에 끝나는지 (다음 작업: 정합성 검증 02:00)"
- "**hourly UPSERT 멱등성 — 3회 재호출 시 row count 변화 0**"

### 이력서 한 줄 (실측 후 확정)

> "30K row × 캠페인 5 × 채널 2 규모 hourly 집계 워커 baseline 박제(3.2초, 5분 윈도우 대비 16배 마진), UPSERT 멱등성 + 비율 지표 정확도 검증, ADR-0007 합산 정책 충족."

### STAR 답변 골격

- **S**ituation: 운영 대시보드는 집계 테이블만 조회 → 워커가 윈도우 안에 끝나야 latency 보장
- **T**ask: hourly 5분 윈도우 + daily 02:00 cron 안에서 처리 완료되는지 검증
- **A**ction: 30K row 시드 → 트리거 호출 → wall-clock 측정 → 멱등성 검증 → KPI 비율 정확도 비교
- **R**esult: [실측값] — 임계치 통과/실패 판정 + 박제 위치

## 다음 단계 (Phase 2 후보)

- 1일치 (240K row) 스케일 측정 — 100만 row 임계까지 확장
- daily 4종 테이블 중 가장 느린 쿼리 식별 → 인덱스 추가 또는 partial aggregate
- 실시간 알림(WARN/CRITICAL) — `system_health_snapshots` 메트릭 도입 (`aggregation_duration_ms` 등)
- daily 롤업 cron 시간 조정 (현재 01:00, 정합성 02:00 사이 마진 검증)

이 항목들은 5/22 발표 후 백로그로 분리.
