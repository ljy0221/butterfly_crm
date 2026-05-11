# 부하 테스트 & 장애 대응 검증 시나리오

> Owner: 이재영
> 최초 작성: 2026-05-11
> 관련 Jira: `S14P31B101-68` (부하 테스트 및 장애 대응 검증 시나리오 작성)
> 관련 문서: [00-mvp-qa-baseline.md](./00-mvp-qa-baseline.md), [01-integration-scenarios.md](./01-integration-scenarios.md)
> 관련 ADR: [ADR-0017](../adr/0017-public-api-key-authentication.md) Public API Key 인증 (100 req/s p99 < 200ms 검증 항목)

본 문서는 MVP 데모 직전(5/15 Code Freeze)까지 **시스템 병목 지점과 복구 절차**를 확인하기 위한 부하/장애 시나리오를 정리한다. **절대 성능 수치 보장이 목적이 아니라** 다음을 확인한다:

1. 정상 흐름이 무너지는 지점(병목)이 어디인지
2. 장애 발생 시 자동 복구가 동작하는지 (재시도·DLQ·헬스체크)
3. 운영자가 인지할 수 있는 신호(메트릭·로그·알림)가 노출되는지

---

## 1. 적용 범위

### 대상 흐름

- **A. 이벤트 수집 API** (`POST /api/v1/events/**`) — 고객사 앱이 직접 호출
- **B. 발송 워커** (RabbitMQ→Worker→FCM/APNS Stub→`notification_logs`)
- **C. 분석 집계 워커** (`HourlyAggregator` 5분 주기, `DailyRollupService` 02:00 KST)
- **D. 정합성 검증 자동 배치** (`ReconciliationScheduler` 02:00 KST)

### 본 문서가 다루지 않는 것

- 실제 FCM/APNS 게이트웨이 부하 (Stub 어댑터로 대체)
- 대규모 (1000 req/s+) 트래픽 — MVP 가정 100 req/s 한도 안에서만
- 장기 부하 (1시간+) — 5분 burst 위주
- 보안 침투 (SQL 인젝션, DoS 등) — S14P31B101-43 운영 정책 에픽

### MVP 통과 기준

- A: 100 req/s 부하에서 p99 < 200ms (ADR-0017 명세값)
- B: 워커 중단 후 재기동 시 큐 적체분 처리 + 멱등성 보장
- C: hourly 워커 1회 누락돼도 누적 카운트 정확 (`MERGE` 멱등)
- D: 외부 회차 0건 또는 어댑터 예외 발생해도 배치 중단 없음

---

## 2. 부하 테스트 시나리오

### 2.1 시나리오 LT-01 — 이벤트 수집 API 단순 부하 (ADR-0017 Verification)

**목적**: `POST /api/v1/events/actions` 가 100 req/s 부하에서 ADR-0017 § "Consequences" 의 latency 명세(p99 < 200ms)를 만족하는지 확인.

**전제**:
- dev 또는 stage 환경
- Project A에 발급된 유효 API Key 1건 (`pk_live_xxxx...`, 32자)
- notification_logs 시드 row 1개 (logId=42, projectId=1, userId=1)
- FCM/APNS Stub 활성화

**도구**: k6 (또는 동등한 HTTP load 도구)

**스크립트 개요** (k6 예시 — 별도 PR로 실제 코드 추가 예정):
```js
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    sustained_100rps: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<200'],   // ADR-0017 명세
    http_req_failed:   ['rate<0.001'],  // 0.1% 미만 실패 허용
  },
};

const payload = JSON.stringify({
  logId: 42, userId: 1, deviceId: 1,
  actionType: 'OPEN', actionAt: new Date().toISOString(),
});

export default function () {
  const res = http.post('http://localhost:8080/api/v1/events/actions', payload, {
    headers: { 'Content-Type': 'application/json', 'X-API-Key': __ENV.API_KEY },
  });
  check(res, { 'status 201': (r) => r.status === 201 });
}
```

**측정 지표**:

| 지표 | 도구 | 통과 기준 |
|---|---|---|
| `http_req_duration` p50/p95/p99 | k6 | p99 < 200ms |
| `http_req_failed` rate | k6 | < 0.1% |
| API 서버 CPU | `docker stats` 또는 Grafana | < 80% 평균 |
| MySQL connections | `SHOW PROCESSLIST` | 풀 한도(보통 20) 미만 |
| Redis ops/sec | `redis-cli INFO stats` | 한도 미만 |
| `notification_logs` row 증가량 (X-API-Key 검증 동작 확인) | DB | 5분 = 100 × 300 = 30,000 row |

**판정**:
- ✅ PASS: 5분 지속 부하에서 p99 < 200ms, 실패 < 0.1%
- ⚠️ WARN: p99 200~500ms 사이 — 병목 1개 식별 후 다음 라운드 재측정
- ❌ FAIL: p99 > 500ms 또는 실패 > 1% — Code Freeze 전 수정 필수

### 2.2 시나리오 LT-02 — 레이트 리밋 동작 확인

**목적**: 100 req/s 초과 시 `ApiKeyAuthenticationFilter` + `RedisRateLimiter` 가 정확히 429를 떨어뜨리는지 확인 (ADR-0017 § "Rate Limit Policy").

**스크립트 변경점** (LT-01에서):
- rate = 150 (한도 100 초과)
- duration = 1m (짧게 — 한도 초과 누적 확인용)
- thresholds 에서 `http_req_failed: ['rate<0.5']` 로 완화

**판정 기준**:
- 200 응답: rate × duration × 100/150 ≈ 4,000건 (한도까지)
- 429 응답: 나머지 ≈ 5,000건 (초과분)
- 429 응답에 `Retry-After` 헤더 존재

### 2.3 시나리오 LT-03 — 분석 집계 워커 부하

**목적**: `HourlyAggregator` 가 1시간치 notification_logs 30K row 를 5분 안에 집계 완료하는지 확인.

**전제**:
- notification_logs 에 1시간치 30K row 시드 (campaign 5개 × channel 2개 × 시간 균등 분포)
- `bucket_hour` UNIQUE 키로 멱등성 보장 (ADR-0007)

**측정 지표**:
| 지표 | 통과 기준 |
|---|---|
| `aggregate(bucketHour, force=false)` 1회 실행 시간 | < 5초 |
| 같은 buckethour 재호출 (멱등) 시간 | < 5초 |
| `campaign_stats_hourly` row 수 | 정확히 5×2=10 row |

---

## 3. 장애 대응 검증 시나리오

### 3.1 시나리오 FT-01 — 발송 워커 중단 → 재기동 (큐 적체분 복구)

**목적**: 발송 워커가 갑작스럽게 중단됐다가 재기동할 때 RabbitMQ 큐의 미처리 메시지가 모두 처리되고 **중복 발송이 발생하지 않는지** 확인.

**절차**:
1. 50건 발송 큐 적재 (Campaign A 즉시 실행 트리거)
2. 워커 시작, 약 20건 처리된 시점에서 워커 SIGTERM
3. RabbitMQ management UI에서 적체 30건 확인
4. 워커 재기동
5. 모든 메시지 처리 완료 대기

**판정**:
- ✅ `notification_logs` row 정확히 50건 (중복/누락 없음)
- ✅ messageKey 기반 멱등성 검증 — 같은 messageKey 의 중복 INSERT 없음
- ✅ status=SENT/DELIVERED 카운트와 큐 발행 카운트 일치

**진단 가이드**:
| 증상 | 의심 |
|---|---|
| row > 50 | 멱등 키 미적용 — `findByMessageKey` 가드 점검 |
| row < 50 | 큐 prefetch 또는 ack 누락 — RabbitMQ ack 모드 점검 |
| status FAILED 비율 높음 | Stub 어댑터 응답 시간 또는 외부 의존 점검 |

### 3.2 시나리오 FT-02 — Kafka DLQ 동작 (notification-send-result)

**목적**: Kafka 컨슈머(`S14P31B101-177`)가 적재 실패한 메시지를 DLQ 토픽으로 보내는지 + 재시도 정책이 동작하는지 (`S14P31B101-180`).

**절차**:
1. notification-send-result 토픽에 의도적으로 잘못된 페이로드 발행 (예: messageKey null)
2. 컨슈머 로그에서 retry 3회 → DLQ 발행 확인
3. DLQ 토픽 메시지 조회: `notification-send-result.DLT`

**판정**:
- ✅ 정상 메시지는 모두 적재됨 (잘못된 1건은 격리)
- ✅ DLQ에 잘못된 메시지 1건 + original_topic, exception_message 헤더 포함
- ✅ retry 3회 후 적재 시도 중단 (무한 루프 없음)

### 3.3 시나리오 FT-03 — Redis 다운 시 레이트 리밋 fallback

**목적**: Redis가 일시 다운돼도 이벤트 수집 API가 동작하는지 (open/close fail policy).

**절차**:
1. `redis-cli SHUTDOWN NOSAVE` (또는 컨테이너 stop)
2. `POST /api/v1/events/actions` 호출
3. 응답 코드 + 로그 메시지 확인
4. Redis 복구 후 동작 정상화 확인

**판정**:
- ⚠️ 결정 필요: **fail-open** (Redis 다운 시 레이트 리밋 우회, 요청 통과) vs **fail-closed** (503 차단)
- 현재 `RedisRateLimiter` 동작은 코드 점검 후 시나리오 확정 — 운영 정책으로 fail-open 권장 (이벤트 적재가 최우선)
- 어떤 정책이든 **로그에 "rate limit unavailable" 경고 1건 이상** 노출

### 3.4 시나리오 FT-04 — DB 연결 끊김 시 발송 워커 동작

**목적**: 발송 워커가 DB 연결 끊김 시 메시지를 ack 하지 않고 큐로 되돌리는지 확인.

**절차**:
1. 50건 발송 큐 적재
2. MySQL 컨테이너 stop (5초)
3. 처리 중 워커 동작 관찰
4. MySQL 복구
5. 큐 적체분 처리 완료 확인

**판정**:
- ✅ DB 끊김 동안 워커가 RuntimeException 발생, 메시지 NACK (큐로 되돌림)
- ✅ DB 복구 후 큐 적체분 재처리 → 최종 row 50건
- ✅ 워커가 OOM 또는 deadlock 없이 회복

### 3.5 시나리오 FT-05 — 정합성 검증 자동 배치 예외 격리 (S14P31B101-187)

**목적**: `ReconciliationScheduler` 가 회차 1건에서 예외가 발생해도 전체 배치를 중단하지 않는지 확인 (이미 단위 테스트로 검증됨, 통합 환경 재검증).

**절차**:
1. 외부 회차 10건 시드 (그중 1건은 의도적으로 `notification_logs.count` 가 매우 큰 값 → SQL 타임아웃 유발)
2. `scheduler.runFor(since)` 수동 호출 또는 cron 트리거
3. `system_health_snapshots` 적재 확인

**판정**:
- ✅ checked = 9 (실패 1건 제외), failed = 1
- ✅ `reconciliation_inconsistent_count` 메트릭 적재됨
- ✅ 로그에 실패 회차 ID + 사유 기록

---

## 4. 실행 가이드

### 4.1 사전 준비

```bash
# 1. dev 환경 띄우기
cd backend
docker-compose -f docker-compose.dev.yml up -d

# 2. 시드 데이터 준비
./scripts/qa/seed-load-test-fixtures.sh   # (별도 PR에서 추가 예정)

# 3. API Key 발급 후 환경변수 설정
export API_KEY=$(curl -X POST .../projects/1/api-keys | jq -r .key)

# 4. k6 설치 (https://k6.io/docs/get-started/installation/)
brew install k6     # macOS
choco install k6    # Windows
```

### 4.2 실행 순서

5/12~14 QA Run 시 다음 순서로 실행:

1. **LT-01** (오늘 5/11 due — ADR-0017 검증) — 30분
2. **LT-02** 레이트 리밋 — 15분
3. **FT-01** 워커 중단 → 재기동 — 30분
4. **FT-02** Kafka DLQ — 15분
5. **FT-03** Redis 다운 → fallback — 15분
6. **FT-04** DB 끊김 — 15분
7. **FT-05** 정합성 배치 격리 — 15분 (이미 단위 테스트로 검증, 통합 재확인만)
8. **LT-03** 분석 집계 워커 — 30분

총 약 3h. 각 시나리오 결과는 별도 보고서(`qa-load-test-{date}.md`)로 기록.

### 4.3 결과 기록 양식 (운영용)

QA Run 보고서(`qa-load-test-{date}.md`)에 다음 양식으로 기록:

```markdown
## LT-01 ADR-0017 부하 테스트 — {YYYY-MM-DD}

| 지표 | 측정값 | 기준 | 판정 |
|---|---|---|---|
| p50 |   ms |   |   |
| p95 |   ms |   |   |
| p99 |   ms | < 200 |   |
| failure rate |   % | < 0.1 |   |
| CPU peak |   % |   |   |

**판정**: PASS / WARN / FAIL

**발견 사항**:
-

**조치**:
-
```

### 4.4 포트폴리오·발표용 Before/After 보존 (필수)

QA Run 보고서(§4.3)는 운영 판정 PASS/FAIL 용이다. 그러나 **5/22 발표 자료 + 이력서·기술 블로그·면접 STAR 답변**의 "성과 측정 축" 근거로 인용하려면 그 이상이 필요하다 — Before 측정·가설·After 측정·트레이드오프가 한 카드에 묶여 있어야 발표에서 한 줄 요약과 근거가 동시에 따라온다.

**보존 위치**: [`docs/optimizations/`](../../../docs/optimizations/README.md) (Optimization Historian 표준 양식 사용)

**규칙**:

1. 본 문서 §2~3의 시나리오 측정값은 **반드시** `docs/optimizations/YYYY-MM-DD_<slug>.md` 카드로 함께 박제한다.
2. 양식·frontmatter·섹션 구성은 [`docs/optimizations/README.md`](../../../docs/optimizations/README.md) 의 표준을 따른다 — 본 문서에서 양식을 새로 정의하지 않는다.
3. 4축(멀티테넌트/비동기/채널 추상화/성과 측정) 매핑은 README §"5/22 발표 4축"에 따라 카드 frontmatter `tags` 에 명시.

**측정 시 지켜야 할 5원칙** (README §"작성 원칙" 요약):

- Before 측정 없이 After 만 기록 **금지** — 비교 불가 → 발표 근거 약화
- Before/After 동일 환경 (다른 환경이면 명시)
- 트레이드오프 누락 금지 (메모리 ↑ vs latency ↓ 같은 양면 기록)
- 추정값과 실측값 분리
- 민감정보 마스킹

**예상 카드 (5/22 발표까지)**:

| slug | 시나리오 | 4축 | 비고 |
|---|---|---|---|
| `event-ingest-api-p99` | LT-01 | 성과 측정 | ADR-0017 명세값 검증 |
| `event-ingest-api-p99-optimized` | LT-01 (재측정) | 성과 측정 | 병목 식별 후 (있을 때만) |
| `aggregation-worker-throughput` | LT-03 | 비동기 / 성과 측정 | hourly UPSERT 30K row |
| `worker-recovery-after-kill` | FT-01 | 비동기 | 멱등성 + 큐 복구 |
| `rate-limit-fallback-policy` | FT-03 | 멀티테넌트 / 성과 측정 | fail-open vs fail-closed |

**원본 첨부** (k6 JSON, EXPLAIN, 프로파일러 PNG)는 `docs/optimizations/_evidence/` 에 같은 날짜 prefix 로 저장.

---

## 5. MVP 통과 임계치 요약

| 시나리오 | 핵심 임계치 | 출처 |
|---|---|---|
| LT-01 | API p99 < 200ms @ 100 req/s | ADR-0017 |
| LT-02 | 한도 초과 시 429 + Retry-After | ADR-0017 |
| LT-03 | 30K row 1시간치 집계 < 5초 | 운영 합의 |
| FT-01 | 워커 재기동 후 중복 발송 0건 | SCHEMA_DESIGN Rule 2 |
| FT-02 | DLQ 적재 + retry 3회 후 중단 | S14P31B101-180 |
| FT-03 | Redis 다운 시 fail-open + 경고 로그 | 본 문서 §3.3 결정 필요 |
| FT-04 | DB 끊김 시 NACK + 복구 후 재처리 | SCHEMA_DESIGN |
| FT-05 | 회차 1건 예외에도 배치 중단 없음 | S14P31B101-187 |

---

## 6. 미해결 / 결정 필요 항목

| # | 내용 | 책임 | due |
|---|---|---|---|
| 1 | FT-03 의 fail-open vs fail-closed 정책 | 역할 1 + 6 합의 | 5/13 |
| 2 | k6 시나리오 코드 추가 (`scripts/qa/k6/`) | 본인 | 5/13 |
| 3 | 시드 스크립트 `seed-load-test-fixtures.sh` | 본인 | 5/13 |
| 4 | LT-01 실측 보고서 (5/13 QA Run #1) | 본인 | 5/14 |

---

## 7. 참고

- ADR-0017 § "Verification Checklist" 미완료 항목 1건 (100 req/s p99 < 200ms) — 본 문서 LT-01 으로 충족
- 데모 시 발표 자료에서 "성과 측정" 축 근거로 LT-01 측정값 인용 권장
- 본 문서 자체는 시나리오 정의 + 도구 사용법까지 — 실제 측정값은 별도 보고서로 추적
