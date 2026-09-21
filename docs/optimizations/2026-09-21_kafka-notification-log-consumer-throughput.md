---
date: 2026-09-21
slug: kafka-notification-log-consumer-throughput
category: PERF
tags: [#performance-axis, #async-axis, #kafka, #batch-listener, #jdbc-batch, #notification-logs]
related-jira: S14P31B101-177
git-before: a4780eeb (8b213378 의 부모)
git-after: 8b213378 ("perf(reporting): Kafka 배치 리스너 + JDBC batchUpdate로 notification_logs 적재 성능 개선")
agent-source: user (프로젝트 시트 "16.2 → 150건/초" 수치의 근거 부재 지적 → 재측정)
status: VERIFIED  # 로컬 환경 상대 비교. 배포 환경 절대값 아님.
before-measured: 2026-09-21 (로컬, 3회)
after-measured: 2026-09-21 (로컬, 3회)
---

# notification-send-result 컨슈머 적재 처리량 — 배치 리스너 + JDBC batchUpdate

## 한 줄 요약

**`NotificationSendResultConsumer` 가 Kafka 메시지를 `notification_logs` 에 적재하는 처리량을 로컬 동일 조건에서 재측정한 결과, 단건 리스너 + JPA `save` 구조(Before)는 61.6~147.2건/초, 배치 리스너 + JDBC `batchUpdate` 구조(After)는 1,413.9~1,527.1건/초. run1 포함 3회 평균 기준 약 16배 (run2·run3 만 보면 약 24배).**

> **이전 수치 정정**: 프로젝트 시트의 "16.2건/초 → 150건/초"는 환경·부하·측정 방법·전후 동일성을 확인할 수 있는 기록이 저장소에 없었다. 본 카드의 재측정값으로 대체하고, 시트의 수치는 사용하지 않는다.

## Situation

- `notification_logs.id` 가 `GenerationType.IDENTITY` 라서 Hibernate JDBC 배치(`batch_size=1000`)가 자동 비활성화된다.
- N=30,000건 발송 시 30,000번의 개별 INSERT + 개별 커밋이 발생했다 (8b213378 커밋 메시지).
- Before 는 메시지마다 `@Transactional` 1회 + Redis SETNX(`RedisMessageKeyGuard`) 왕복 1회가 추가된다.

## Action (8b213378 의 변경)

| 항목 | Before | After |
|---|---|---|
| 리스너 | 단건 `consume(payload)` | `List<Payload>` 배치 수신 (`setBatchListener(true)`) |
| Ack | `AckMode.RECORD` | `AckMode.BATCH` |
| 적재 | JPA `save` (메시지당 INSERT + 커밋) | `NamedParameterJdbcTemplate.batchUpdate` + `INSERT IGNORE` |
| 멱등성 | Redis SETNX 가드 + DB UNIQUE | `INSERT IGNORE` (message_key UNIQUE) |
| poll | 기본값 | `max.poll.records=500` |

## Result — 재측정

### 측정 조건

| 항목 | 값 |
|---|---|
| 환경 | 로컬 Windows 11 PC (12코어), Docker Desktop (메모리 8GB) |
| 인프라 | EmbeddedKafka (파티션 1, 컨슈머 1) + Testcontainers MySQL 8.0. Before 만 Redis 7-alpine 추가 (Before 코드가 Redis 가드를 사용) |
| 부하 | 유니크 `messageKey` 10,000건을 단일 프로듀서로 일괄 발행. 워밍업 500건 후 3회 실행, 매 회 `DELETE FROM notification_logs` |
| 측정 방법 | 발행 시작 ~ `COUNT(*)` 가 10,000 에 도달할 때까지의 wall-clock (50ms 폴링). 벤치 코드는 `_evidence/` 에 보존 |
| 전후 동일성 | 두 커밋을 각각 git worktree 로 분리, **동일한 벤치 테스트**·동일한 데이터·동일한 PC 에서 순차 실행 |

### 결과

| | Before (`a4780eeb`) | After (`8b213378`) |
|---|---|---|
| warmup (500건) | 92.2건/초 | 651.4건/초 |
| run1 | 147.2건/초 (67.93s) | 1,413.9건/초 (7.07s) |
| run2 | 66.6건/초 (150.24s) | 1,514.9건/초 (6.60s) |
| run3 | 61.6건/초 (162.39s) | 1,527.1건/초 (6.55s) |
| 평균 (run1~3) | 91.8건/초 | 1,485.3건/초 |
| 평균 (run2~3) | 64.1건/초 | 1,521.0건/초 |

- 배율: run1~3 평균 기준 1,485.3 / 91.8 ≈ **16.2배**, run2~3 평균 기준 1,521.0 / 64.1 ≈ **23.7배**.
  - Before run1(이상치)을 제외하면 배율이 커지므로, 보수적으로 **run1 을 포함한 3회 평균 기준 약 16배**를 대표값으로 사용한다.
- Before 는 반복할수록 느려졌고(147 → 67 → 62), After 는 안정적이다 (편차 약 8%).

### 증거

- `_evidence/2026-09-21_kafka-consumer-bench-before.txt` / `-after.txt` — 실행 출력
- `_evidence/2026-09-21_kafka-consumer-bench-before.java.txt` / `-after.java.txt` — 벤치 테스트 코드

## 한계 (반드시 함께 표기)

1. **로컬 상대 비교**다. 브로커·DB 가 모두 로컬 컨테이너라 배포 환경(k8s)의 절대값이 아니다.
2. 파티션 1, 컨슈머 1. 동시 처리(파티션 확장) 효과는 측정하지 않았다.
3. Before run1(147.2건/초)은 run2·3 과 2배 이상 차이 나는 이상치다. 원인 미확인 (`DELETE` 후 테이블 상태 등 추정 가능하나 검증하지 않음).
4. **스키마 조정**: After 벤치에만 `updated_at` 컬럼을 `ALTER TABLE` 로 추가했다 (아래 이슈 참조). Before 는 해당 컬럼을 쓰지 않으므로 영향이 없을 것으로 보나, 엄밀한 동일 스키마 비교는 아니다.
5. After 실행 중 이전에 중단한 실행의 MySQL 컨테이너 1개가 남아 있었다 (After 에 불리한 방향의 간섭 가능성).
6. 표본은 각 3회. 통계적 유의성을 주장하지 않는다.

## 발견 이슈

`NotificationLogBulkRepository` 의 INSERT 가 `updated_at` 컬럼을 사용하지만 `NotificationLog` 엔티티와 Flyway 마이그레이션(V2/V16/V21)에 해당 컬럼이 없다. 상세와 확인 결과는 아래 "이슈 검증" 참조.

## 이슈 검증

**결론: 확인됨 (2026-09-21).** `8b213378` 이 도입한 결함이며 develop HEAD(`42732056`)에도 남아 있다.

### 검증 절차와 결과

1. `NotificationLogBulkRepository.INSERT_SQL` 이 `updated_at` 컬럼을 사용한다 (`sent_at, delivered_at, created_at, updated_at, message_key`).
2. `NotificationLog` 엔티티는 `updated_at` 필드가 없고, `git log -S"updated_at"` 상 `notification_logs` 에 이 컬럼을 추가한 마이그레이션이 없다.
3. 빈 MySQL 8.0 에 `V1~V21` 을 순서대로 적용한 뒤 `SHOW COLUMNS FROM notification_logs` 를 조회했다 → **`updated_at` 없음** (`title/body/click_url` 은 V16 으로 존재).
4. 같은 컬럼을 쓰는 INSERT 를 그대로 실행 → `ERROR 1054 (42S22): Unknown column 'updated_at' in 'field list'`.
5. 운영 설정은 `ddl-auto: update` 이지만 엔티티에 없는 컬럼은 생성하지 않으므로 운영에서도 동일하다고 판단한다.

### 영향 (추정 포함)

- 배치 리스너가 `BadSqlGrammarException` 을 던져 `notification-send-result` 메시지가 적재되지 않고 DLQ 로 간다. 통합 테스트 재현 로그에서도 DLQ 발행이 다시 실패(`No matching delegate for type ... supported types: [[B, String]`)해 메시지가 재시도 루프에 걸리는 것이 관찰됐다.
- 그 결과 `notification_logs` 가 비어 hourly/daily 집계와 대시보드 수치에 영향을 줄 수 있다.
- **미확인**: 배포된 운영/스테이징 DB 에 `updated_at` 이 수동으로 추가돼 있는지는 확인하지 못했다. 운영 DB 에서 `SHOW COLUMNS FROM notification_logs` 로 확인해야 한다.
- 8b213378 이후 정상 적재를 검증한 테스트가 없다 (`4ff4006c` 에서 불안정한 컨슈머 통합 테스트를 제거).

### 조치: 벌크 INSERT 에서 `updated_at` 제거 (적용)

`NotificationLogBulkRepository.INSERT_SQL` 의 `updated_at` 컬럼과 대응 값(`:createdAt` 중복)을 제거했다. 엔티티·스키마와 일치하는 최소 변경이다.

### 수정 후 재측정 (develop HEAD `42732056` + 위 수정, `updated_at` ALTER 보정 없음)

| 구성 | run1 | run2 | run3 |
|---|---|---|---|
| 수정만 적용 (HEAD 그대로) | 246.3 | 129.9 | 84.9 |
| 수정 + `@Transactional` 임시 복원 | 1,334.3 | 1,444.9 | 984.8 |

(건/초, 10,000건, 동일 벤치·동일 PC. 10,000건 전부 적재 확인. 위 두 번째 행은 측정용 임시 변경이며 커밋하지 않았다.)

## 추가 발견 — `@Transactional` 제거로 현재 develop 의 처리량이 저하됨

- `4ff4006c` (`fix(test): 배치 리스너 전환 후 불안정한 Kafka consumer 통합 테스트 제거`)가 테스트 삭제와 함께 **프로덕션 코드 `NotificationSendResultConsumer.consume` 의 `@Transactional` 도 제거**했다.
- 트랜잭션이 없으면 `batchUpdate` 가 자동 커밋으로 동작해 행 단위로 커밋되는 것으로 보이며, 측정 결과가 이를 뒷받침한다: 위 표처럼 `@Transactional` 이 없으면 **85~246건/초**(반복할수록 하락), 있으면 **985~1,445건/초**.
- 따라서 본 카드의 After(1,413.9~1,527.1건/초)는 `8b213378` 시점의 값이고, **현재 develop 은 그 수치를 내지 못한다.** 발표·이력서에 쓰기 전에 `@Transactional` 복원 여부를 결정해야 한다.
- 복원 시 동작 변화: 배치 도중 예외가 나면 그 배치 전체가 롤백되어 재전달된다 (8b213378 의 원래 설계). 제거가 의도된 것인지(테스트 불안정 회피 목적이었는지)는 확인하지 못했다.
- 미결: `@Transactional` 복원은 별도 결정으로 두고 본 커밋에는 포함하지 않았다.
