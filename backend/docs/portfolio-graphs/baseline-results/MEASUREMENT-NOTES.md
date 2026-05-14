# Baseline 측정 메모 — S14P31B101-294 Phase A

> partition=1, concurrency=1, AUTO ack, 직렬 lookup, `.get()` 동기 publish 의 **BEFORE** 수치.
> 본 디렉토리의 raw 파일은 모든 가설 (H1~H10) 의 reference point.

## 측정 환경

| 항목 | 값 |
|---|---|
| **일시** | 2026-05-14 22:15 ~ 22:23 (KST) |
| **호스트** | macOS (darwin 25.4.0) |
| **Spring Boot** | 3.5 / Java 21, profile=api,worker (한 process 통합) |
| **Kafka** | apache/kafka:3.7.0, partition=1, replication=1 |
| **MySQL** | mysql:8.0 (3307) |
| **부하 도구** | K6 0.53.0 (docker run) |
| **계정** | owner@test.local (password reset to BCrypt `test1234!`) |
| **데이터** | users 1000, segment_members 1000 (segment_id=1), user_tags 100, campaigns 130 DRAFT |

## 측정 시퀀스 (3회 — 1차 race condition → 2차 race fix → 3차 JVM warmup 적용)

### 1차 측정 — 측정 방법론의 결함 발견 (`baseline-raw-20260514-221518.*`)

| 시나리오 | 결과 |
|---|---|
| B (burst 50 VU, 100 iter) | 32/100 success — **K6 race condition** |
| E (single VU, 30 iter) | 1/30 success — sanity test 의 132 ACTIVE 영향 |

**원인**: K6 script 가 `__ITER % CAMPAIGN_IDS.length` 사용. shared-iterations executor 에서 `__ITER` 는 VU local count → 50 VU 가 동시에 같은 campaign ID 노림 → 1개만 성공, 49 conflict (`HTTP 409 CAMPAIGN_019 허용되지 않은 캠페인 상태 전이`).

**측정 방법론 진화** — 포트폴리오 ADR 스토리 (방법 → 결함 발견 → 개선) 자료. 1차 결과도 영구 박제.

### 2차 측정 — race condition fix 적용 (`baseline-raw-20260514-222037.*`)

K6 script 수정: `__ITER` → `exec.scenario.iterationInTest` (scenario 단위 unique sequential index).

| 시나리오 | metric | 값 |
|---|---|---|
| **B (burst 50 VU, 100 iter)** | success | 100/100 ✅ |
| | avg | 1453ms |
| | p95 | 2216ms |
| | p99/max | 3379ms |
| | min | 126ms |
| **E (single VU, 30 iter sequential)** | success | 30/30 ✅ |
| | avg | 77ms |
| | p95 | 160ms |
| | p99/max | 647ms |
| | min | 26ms |
| **합계** | 130 reqs / 130 success / 0 failure | ~130초 |

### 3차 측정 — JVM warmup phase 적용 (`baseline-raw-20260514-230813.*`) — **정식 BEFORE**

H1 cold 측정에서 E 가 16ms 로 비현실적으로 빨라진 게 JVM JIT warmup 효과 발견. 측정 방법론 추가 진화: K6 에 warmup scenario (10 VU × 100 iter health check, 40s) 추가 → main 시나리오 시작 전 JVM JIT 안정화.

| 시나리오 | metric | 값 |
|---|---|---|
| **B (burst 50 VU, 100 iter)** | success | 100/100 ✅ |
| | avg | **632ms** (↓57% vs 2차 cold) |
| | p95 | **1192ms** (↓46% vs 2차 cold) |
| | max | 1258ms |
| **E (single VU, 30 iter)** | success | 30/30 ✅ |
| | avg | **38.5ms** (↓50% vs 2차 cold) |
| | p95 | **177ms** |
| | max | 298ms |
| **B/E ratio** | avg | 16.4x — warmed 상태에서도 자원 경합 dominant |
| | p95 | 6.7x |

이게 **정식 BEFORE** — 모든 가설 (H1~H10) 의 fair 비교 reference point.

### 해석 — 자원 경합 패턴

- **single VU (E warmed)**: avg 38.5ms — Spring app + Kafka publish (.get() 동기) + DB transaction
- **burst 50 VU (B warmed)**: avg 632ms ← **~16배 느려짐**
  - 원인 후보: HTTP thread contention / DB lock contention / Kafka publish.get() 직렬화 / Spring transaction 충돌
  - **이게 Throughput 시나리오의 핵심 측정값** — H1~H4 가설로 변화 추적

## Raw 파일 인벤토리

| 파일 | 용도 |
|---|---|
| `baseline-raw-20260514-222037.json` | **2차 측정 (정식 baseline)** — K6 모든 sample (~630KB) |
| `baseline-raw-20260514-222037.csv` | **2차 측정 CSV** — Excel/Pandas 친화 (~340KB) |
| `baseline-summary.json` | **K6 통계 요약** (p50/p95/p99/avg/min/max/throughput/error_rate) |
| `baseline-raw-20260514-221518.json` | 1차 (race condition) raw — 측정 방법론 진화 자료로 보존 |
| `baseline-raw-20260514-221518.csv` | 1차 CSV |

## Prometheus snapshot

`../snapshots/baseline-20260514-fixed-*.json` 에 9 query 의 시계열 raw (5초 step, ~150s 구간):

| query | 데이터 유무 | 비고 |
|---|---|---|
| `kafka_listener_rate` | ✅ (5 listener × 30 samples) | container 별 분리 — topic label 없음 (Spring Kafka micrometer 기본 한계) |
| `kafka_listener_p95/p99` | ❌ empty | bucket 누적 부족 — 측정 시간 짧음 (H3/H4 측정 시 충분해질 것) |
| `kafka_listener_error_rate` | ❌ empty | 에러 없음 (rate=0) |
| `kafka_template_p95` | ❌ empty | producer metric 활성화 필요 (Spring Kafka template observation enable) |
| `http_activate_rate` | ✅ | 정식 부하 traffic 확인 |
| `http_activate_p95` | ❌ empty | bucket 누적 부족 |
| `process_cpu` | ✅ | 시스템 자원 추적 |
| `jvm_memory_used` | ✅ | heap 사용 추적 |

**개선 백로그** (Phase B 진입 전):
- Spring Kafka observation 활성화 — `factory.getContainerProperties().setObservationEnabled(true)` → topic label 노출
- KafkaTemplate observation 활성화 — `template.setObservationEnabled(true)`
- 측정 시간 더 길게 (60s+ idle 후 측정 또는 측정 후 60s+ scrape 추가) → histogram bucket 충분

## DB 상태 변화 (측정 후)

- 1차 측정: 33개 캠페인 ACTIVE 전이
- DB reset (UPDATE campaign_status='DRAFT') 후 2차 측정 → 130개 모두 ACTIVE 전이 (1차 + 2차 + sanity test)
- 다음 가설 측정 전 reset SQL 적용:
  ```sql
  UPDATE campaigns SET campaign_status='DRAFT', version=0 WHERE campaign_name LIKE 'PerfTest-%';
  DELETE FROM campaign_executions WHERE campaign_id IN (SELECT campaign_id FROM campaigns WHERE campaign_name LIKE 'PerfTest-%');
  ```

## 다음 가설 측정 시 변경 사항

각 가설 측정 시 **변경 한 곳만** 적용 → 재기동 → reset → run-baseline.sh 실행. 결과 디렉토리 분리 (`h1-partition-only/` 등).

- **H1**: `WorkerKafkaConfig.CAMPAIGN_SEND_REQUESTED_PARTITIONS_BASELINE = 8` (concurrency 1 유지)
- **H2**: partition=1 유지 + `factory.setConcurrency(4)`
- **H3**: partition=8 + concurrency=4
- **H4**: H3 + Processor 내부 lookup 병렬화

가설별 측정은 Phase B (`task #52`) 에서 진행.
