# scripts/qa/k6/ — 부하/장애 시나리오 실행 스크립트

> Owner: 이재영
> 관련 Jira: `S14P31B101-68`
> 관련 문서: [backend/docs/qa/03-load-and-failure-scenarios.md](../../../backend/docs/qa/03-load-and-failure-scenarios.md)
> 관련 ADR: [ADR-0017](../../../backend/docs/adr/0017-public-api-key-authentication.md)
> 관련 카드: [docs/optimizations/2026-05-11_event-ingest-api-p99.md](../../../docs/optimizations/2026-05-11_event-ingest-api-p99.md), [..._aggregation-worker-throughput.md](../../../docs/optimizations/2026-05-11_aggregation-worker-throughput.md)

본 디렉토리는 03-load-and-failure-scenarios.md 에 정의된 부하 시나리오를 **실제로 실행 가능한 k6 스크립트** 로 정착한다. 측정 결과는 `docs/optimizations/` BEFORE-ONLY 카드에 채워 COMPLETE 로 승격.

---

## 0. 준비

### 0.1 k6 설치

```bash
brew install k6          # macOS
choco install k6         # Windows
sudo apt install k6      # Linux (https://k6.io/docs/get-started/installation/)
```

### 0.2 시드 데이터 적재

`scripts/qa/seed/seed-load-test-fixtures.sql` 을 dev MySQL 에 실행하면 다음이 정착된다:

| 항목 | 값 |
|---|---|
| Project | id=1, name="Load Test Project" |
| API Key | `pk_live_loadtest000000000000abcd` (32자, SHA-256 해시 등록) |
| notification_logs row | logId=42, projectId=1, userId=1, deviceId=1 |
| (LT-03 용) notification_logs 30K row | campaign 5 × channel 2 × bucketHour 분포 |

```bash
mysql -h localhost -P 3307 -u root -p butterfly < scripts/qa/seed/seed-load-test-fixtures.sql
```

### 0.3 애플리케이션 기동

```bash
cd backend
./gradlew :butterfly-api:bootRun
# 또는 docker-compose up
```

`actuator/health` 가 UP 응답하면 시나리오 실행 가능.

---

## 1. 시나리오별 실행

### LT-01 — 이벤트 수집 API @ 100 req/s 5분 (ADR-0017 검증)

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e API_KEY=pk_live_loadtest000000000000abcd \
  -e LOG_ID=42 \
  -e PROJECT_ID=1 \
  -e USER_ID=1 \
  -e DEVICE_ID=1 \
  --summary-export=lt01-summary.json \
  --out json=lt01-raw.json \
  scripts/qa/k6/lt01-event-ingest.js
```

**임계치**:

- `http_req_duration` p99 < 200ms (ADR-0017)
- `http_req_failed` rate < 0.1%

**측정 후**:

1. `lt01-summary.json`, `lt01-raw.json` 을 `docs/optimizations/_evidence/2026-05-XX_event-ingest-api-p99-k6.json` 으로 이동
2. `docs/optimizations/2026-05-11_event-ingest-api-p99.md` 의 Result 표에 p50/p95/p99 채움
3. status BEFORE-ONLY → COMPLETE 갱신

### LT-02 — 레이트 리밋 @ 150 req/s 1분

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e API_KEY=pk_live_loadtest000000000000abcd \
  -e LOG_ID=42 \
  --summary-export=lt02-summary.json \
  scripts/qa/k6/lt02-rate-limit.js
```

**임계치**:

- 201 응답: 최소 3000건 (한도 안 통과분)
- 429 응답: 최소 1000건 (한도 초과 차단)
- 429 응답에 `Retry-After` 헤더 1건 이상 세팅
- 그 외 응답 (401/500): 10건 미만

### LT-03 — 분석 집계 워커 (한 번 호출, 시간 측정)

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e BUCKET_HOUR=2026-05-14T00:00:00 \
  --summary-export=lt03-summary.json \
  scripts/qa/k6/lt03-aggregation.js
```

**임계치**:

- hourly 단일 집계 < 5000ms (avg < 4000ms)
- hourly 멱등 재호출 < 5000ms
- daily 4종 롤업 < 30000ms (avg < 10000ms)

**전제**: 시드 스크립트로 BUCKET_HOUR 직전 1시간치 30K row 적재 완료.

---

## 2. CI 통합 (선택)

GitLab CI 또는 GitHub Actions 에서 k6 실행:

```yaml
# .gitlab-ci.yml 예시 (별도 PR 권장)
load-test:
  stage: post-test
  image: grafana/k6:latest
  script:
    - k6 run -e BASE_URL=http://api:8080 -e API_KEY=$LOAD_TEST_API_KEY scripts/qa/k6/lt01-event-ingest.js
  artifacts:
    paths:
      - lt01-summary.json
      - lt01-raw.json
    when: always
  only:
    - schedules   # nightly run only — CI 마다 돌리면 dev 환경 부하 부담
```

---

## 3. 결과 박제 절차 (Optimization Historian 양식)

1. k6 실행 → `--summary-export=`, `--out json=` 옵션으로 결과 파일 생성
2. 결과 파일을 `docs/optimizations/_evidence/{YYYY-MM-DD}_{slug}-k6.json` 으로 이동
3. 해당 카드 (`docs/optimizations/{YYYY-MM-DD}_{slug}.md`) 의 Result 섹션 채움
4. frontmatter `status` 를 BEFORE-ONLY → COMPLETE 로 갱신
5. INDEX.md 의 시간순 표 + 카테고리별 + 4축 매핑 갱신

상세 양식: [docs/optimizations/README.md](../../../docs/optimizations/README.md)

---

## 4. 트러블슈팅

### `dial tcp: connection refused`

애플리케이션 미기동. `actuator/health` 확인 후 재시도.

### `401 AUTH_401` (LT-01/02)

API Key 미발급 또는 SHA-256 불일치. 시드 스크립트 재실행 후 환경 변수 `API_KEY` 가 정확한지 확인.

### `400 LOG_NOT_FOUND`

`LOG_ID` 가 시드된 row 와 다름. 또는 도메인 검증(193 도메인 검증 머지본)에서 `resolvedProjectId` 불일치. 시드의 logId 와 환경 변수 일치 확인.

### LT-03 의 hourly 호출이 매우 느림 (10초+)

원인 1: 시드 데이터 30K row 가 모두 같은 bucket 에 들어있지 않음 → 윈도우 외 row 카운트. 시드 SQL 의 `created_at` 분포 확인.

원인 2: `idx_notification_logs_project_time` 인덱스 미적용 → `EXPLAIN` 으로 확인.

병목 식별 시 → 최적화 PR + 별도 카드 (`event-ingest-api-p99-optimized` 또는 `aggregation-worker-throughput-optimized`).

---

## 5. 시나리오 추가 절차

새 LT-* 또는 FT-* 시나리오 추가 시:

1. `scripts/qa/k6/ltXX-{slug}.js` 또는 `ftXX-{slug}.js` 신규
2. `common.js` 의 헬퍼 재사용
3. `handleSummary()` 로 운영자 친화적인 stdout + JSON 박제 포함
4. 본 README §1 에 실행 방법 추가
5. 03-load-and-failure-scenarios.md §2/§3 시나리오 정의와 일치 유지
