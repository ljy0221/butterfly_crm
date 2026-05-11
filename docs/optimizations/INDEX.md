# Optimization Index

> butterfly 프로젝트의 최적화·리팩터링·아키텍처 결정 기록.
> 모든 항목은 Before/After 증거를 포함한다 (또는 BEFORE-ONLY 상태로 박제 후 후속 보강).
>
> 작성 가이드는 [`README.md`](README.md), 템플릿은 카드 본문 참조.

## 카테고리

- **PERF** — 성능 (응답시간, 쿼리, 메모리)
- **SCHEMA** — 스키마/마이그레이션
- **ARCH** — 아키텍처/모듈/SPI
- **SECURITY** — 보안·감사
- **DX** — 개발자 경험 (빌드, 테스트, CI, 게이트)
- **DECISION** — 대안 비교 후 채택

## 시간순

| 날짜 | 카테고리 | 슬러그 | 한 줄 요약 | 상태 |
| --- | --- | --- | --- | --- |
| 2026-05-11 | DX | [testcontainers-reuse](2026-05-11_testcontainers-reuse.md) | testsupport 베이스 클래스 3종(MySQL/Kafka/Redis)에 `.withReuse(true)` + `.gitlab-ci.yml`에 `TESTCONTAINERS_REUSE_ENABLE=true` 단일 PR(파일 4개) 머지로 integration-test 잡 wall-clock **14m 02s → 9m 56s (-29%)**, script **13m 02s → 8m 55s (-32%)**. 부작용(MySQL shutdown drop 실패 로그 노이즈, 테스트 PASS) + 추가 발견(컨텍스트 캐시 hit 0건, EmbeddedKafka 모듈 reuse 효과 0) 박제. 1회 표본 (225 머지 영향 분리 불가) → develop 5건 누적으로 보강 예정. | VERIFIED |
| 2026-05-11 | PERF | [event-ingest-api-p99](2026-05-11_event-ingest-api-p99.md) | ADR-0017 명세 "100 req/s p99 < 200ms" 가 실제로 충족되는지 5/13 QA Run #1 에서 1차 실측. 인증 필터(205) + 도메인 검증(193) + 레이트 리밋(Redis) 흐름의 latency baseline 박제. 측정 후 COMPLETE 승격, 병목 식별 시 별도 카드(`event-ingest-api-p99-optimized`)로 Before/After 1쌍 분리. | BEFORE-ONLY |
| 2026-05-11 | PERF | [aggregation-worker-throughput](2026-05-11_aggregation-worker-throughput.md) | 30K row 1시간치 hourly 집계 + daily 4종 롤업이 윈도우 안에 끝나는지 5/14 QA Run #2 에서 1차 실측. HourlyAggregator(124-A) + DailyRollupService(191 Refactor) 흐름의 처리량/latency baseline 박제. 멱등성·KPI 비율 정확도 동시 검증. | BEFORE-ONLY |
| 2026-05-11 | DX | [ci-pipeline-dedup-mr-workflow](2026-05-11_ci-pipeline-dedup-mr-workflow.md) | `.gitlab-ci.yml` 최상위 `workflow:` 블록 + 잡 rules `merge_request_event` 분기 도입 + `package` 잡 MR 차단으로 branch ↔ MR pipeline 중복 실행 시나리오 3개를 룰 정의 기준 0개로 차단. **MR !137 dry-run 검증 완료, 머지 후 develop pipeline에서 package 잡 1m 59s 정상 실행 확인. 추가 발견: integration-test critical path / cache `pull-push` 31s 낭비 / `:latest` 태그 신뢰도 / cgroup limit 부재** | VERIFIED |
| 2026-05-07 | DX | [ci-gate-restoration](2026-05-07_ci-gate-restoration.md) | unit/integration `allow_failure: true` 제거 + Flyway V7/V8 회귀 fix + Jib latest 태그 한정 + readiness 폴링 — DoD "CI 그린" 측정 가능성 복원. **2차 실측: 전체 pipeline 10m 53s GREEN (DoD 30분 -64% 마진), 잡별 wall-clock 4건 박제, Jib latest 미푸시 결정적 증거 확보, 게이트가 회귀 누적 2건 정상 차단** | AFTER |

## 5/22 발표 4축 매핑

### 멀티테넌트

- _(아직 카드 없음)_

### 비동기

- 2026-05-11 [aggregation-worker-throughput](2026-05-11_aggregation-worker-throughput.md) — Spring `@Scheduled` 5분/일 cron + MERGE 멱등 + force 재집계 baseline (BEFORE-ONLY) ⭐
- 2026-05-11 [testcontainers-reuse](2026-05-11_testcontainers-reuse.md) — (간접) Kafka 컨테이너 reuse로 reporting/notification 모듈 통합 테스트 wall-clock 단축 기여 (VERIFIED, 1회 표본)
- _(추가 후보: S14P31B101-180 Kafka DLQ/재시도 카드화)_

### 채널 추상화

- _(아직 카드 없음)_

### 성과 측정

- 2026-05-11 [testcontainers-reuse](2026-05-11_testcontainers-reuse.md) — integration-test 잡 wall-clock -29% / script -32%, DoD 30분 SLA 마진 추가 확장 (VERIFIED, 1회 표본)
- 2026-05-11 [event-ingest-api-p99](2026-05-11_event-ingest-api-p99.md) — ADR-0017 명세 p99 < 200ms 100 req/s 실측 baseline (BEFORE-ONLY) ⭐
- 2026-05-11 [aggregation-worker-throughput](2026-05-11_aggregation-worker-throughput.md) — 집계 워커 5분/일 cron 윈도우 latency baseline (BEFORE-ONLY) ⭐
- 2026-05-11 [ci-pipeline-dedup-mr-workflow](2026-05-11_ci-pipeline-dedup-mr-workflow.md) — CI 중복 트리거 제거로 DoD 30분 SLA 마진 추가 확장 + GHCR 태그 오염 차단 (VERIFIED, package 1m 59s)
- 2026-05-07 [ci-gate-restoration](2026-05-07_ci-gate-restoration.md) — DoD 측정 가능성 복원

## 카테고리별

### DX

- 2026-05-11 [testcontainers-reuse](2026-05-11_testcontainers-reuse.md) — Testcontainers `withReuse(true)` + `TESTCONTAINERS_REUSE_ENABLE=true` 단일 PR로 integration-test 잡 -29% / script -32% (VERIFIED, 1회 표본)
- 2026-05-11 [ci-pipeline-dedup-mr-workflow](2026-05-11_ci-pipeline-dedup-mr-workflow.md) — workflow 블록 + MR pipeline 전환 (VERIFIED)
- 2026-05-07 [ci-gate-restoration](2026-05-07_ci-gate-restoration.md)

### PERF

- 2026-05-11 [event-ingest-api-p99](2026-05-11_event-ingest-api-p99.md) — ADR-0017 100 req/s p99 < 200ms 검증 (BEFORE-ONLY)
- 2026-05-11 [aggregation-worker-throughput](2026-05-11_aggregation-worker-throughput.md) — 30K row hourly + daily 4종 윈도우 latency (BEFORE-ONLY)

### SCHEMA / ARCH / SECURITY / DECISION

- _(아직 카드 없음)_

## After 측정 진행 상황

### 1차 + 2차 실측 완료 (AFTER 승격)

| 카드 | 1차 (commit 14ac004) | 2차 (commit 56b0e5e) | 핵심 결과 |
| --- | --- | --- | --- |
| [ci-gate-restoration](2026-05-07_ci-gate-restoration.md) | integration-test 7m 5s + ButterflyApiApplicationTests 1 fail (게이트 차단 입증) | 전체 pipeline 10m 53s **PASSED**, 잡별 wall-clock 4건 (unit 1m 58s / integration 8m 24s / checkstyle 2m 59s / package 1m 4s), Jib `latest` 미푸시 결정적 증거 | DoD 30분 SLA 대비 **-64% 마진**, readiness 폴링 1s (1차/2차 모두), 회귀 누적 2건 차단·fix, feature 브랜치는 gitSha + branch slug 2개만 푸시 |

### 1차 실측 완료 (VERIFIED, 단일 표본)

| 카드 | 1차 (MR !144 commit 7be5475) | 2차 (develop SHA bd2b560, 226 머지 후 5건) | 3차 (228 PR SHA 3b85a182, cache miss 강제) | 핵심 결과 | 보강 트리거 |
| --- | --- | --- | --- | --- | --- |
| [testcontainers-reuse](2026-05-11_testcontainers-reuse.md) | integration-test 잡 14m 02s → 9m 56s (-29%), script 13m 02s → 8m 55s (-32%), 데이터 누수 실패 0건 | **integration-test 잡 2m 00s** (Gradle Build Cache hit fast path — 모든 모듈 `:integrationTest` FROM-CACHE) — reuse 단독 효과 아님, 정직 박제 | **integration-test 잡 10m 51s** (Spring Security 모듈 광범위 변경으로 거의 전체 cache miss). reuse 단독 효과 -23% 측정 → 1차 -29%와 합쳐 **reuse 진짜 효과 -25% 추정 확정** | effective wall-clock은 PR 시나리오에 따라 **2m (cache hit) ~ 11m (cache miss) 범위**로 4축 측정 완료. DoD 30분 SLA 모든 시나리오 통과 (-93% ~ -64% margin) | develop pipeline 5건 누적으로 p50/p95 정밀도 +20% 향상 |

### 머지 후 측정 대기 항목 (후속 측정 카드 분리 추적)

| 카드 | 측정 트리거 | 남은 측정 항목 |
| --- | --- | --- |
| [ci-gate-restoration](2026-05-07_ci-gate-restoration.md) | MR #96 develop 머지 후, develop 파이프라인 5건 누적 (5/14까지) | 머지→빌드 완료 SLA(DoD 30분), develop pipeline duration p50/p95(5건), 통합 테스트 깜빡임 비율(5건 누적 retry job 발생률) |
| [ci-pipeline-dedup-mr-workflow](2026-05-11_ci-pipeline-dedup-mr-workflow.md) | 본 PR 머지 직전 dry-run MR 1건 + 머지 후 develop 5건 / MR pipeline 5건 (5/14까지) | dry-run MR에서 동일 SHA 1개 pipeline 표시 결정적 증거, MR pipeline 잡 매트릭스(`package` 미매칭), develop pipeline duration p50/p95, MR pipeline duration p50/p95, GHCR 태그 누적 추세 정지, `package` 회귀 0건 |
| [testcontainers-reuse](2026-05-11_testcontainers-reuse.md) | MR !144 develop 머지 후, develop 파이프라인 5건 누적 (5/15까지) | reuse 단독 효과 분리 검증 (S14P31B101-225 머지 영향 분리), develop integration-test 잡 wall-clock p50/p95(5건), MySQL Communications link failure 로그 노이즈 빈도(5건 누적), 후속 B/C/D안 추정값 정확도 학습 |
