---
date: 2026-05-11
slug: event-ingest-api-p99
category: PERF
tags: [#performance-axis, #api, #rate-limit, #k6, #adr-0017]
related-jira: S14P31B101-68
related-adr: ADR-0017
related-branch: Docs/S14P31B101-68_LoadFailureScenarios
git-before: 16f53f3 (origin/develop, "Merge branch 'Feat/S14P31B101-187_HealthSnapshotMetric'")
git-after: TBD (5/13 QA Run #1 시점 측정 commit)
agent-source: user (5/11 부하 테스트 시나리오 작성 일환), optimization-historian (본 카드 양식)
status: BEFORE-ONLY
before-measured: TBD (5/13 QA Run #1 — 환경/시드 정착 후 첫 baseline)
after-measured: TBD
---

# `POST /api/v1/events/actions` — ADR-0017 100 req/s p99 < 200ms 검증

## 한 줄 요약

**ADR-0017 § "Consequences" 의 latency 명세 "100 req/s 부하에서 p99 < 200ms" 가 실제로 충족되는지 5/13 QA Run #1 에서 1차 실측한다. 본 카드는 측정 전 baseline 박제용 BEFORE-ONLY 카드로 출발 — Before(현 develop 상태) + 가설 + 측정 절차 + 첨부 위치까지 미리 정착해 두고, 측정 후 Result 섹션을 채워 COMPLETE 로 승격한다.**

> 측정값은 5/22 발표 슬라이드 "성과 측정 축"의 핵심 근거이자, 이력서/블로그/면접 STAR 답변에 그대로 인용된다. 따라서 단발 측정으로 끝내지 않고 Before/After 비교 형식으로 박제한다 (병목이 식별되면 최적화 PR 후 재측정 → 카드 분리).

## Situation (배경)

### ADR-0017 명세

ADR-0017(SDK Public API Key 인증) § "Consequences" — Negative 항목:

> **DB 직접 조회**: 캐시가 없어 매 SDK 요청마다 1회 SELECT — 단, 인덱스(unique key) 사용으로 latency 영향 미미. **부하 테스트(S14P31B101-68) 에서 p99 < 200ms 확인 예정.**

또한 § "Rate Limit Policy":

| 항목 | 값 |
|---|---|
| 기준 단위 | 프로젝트별 (scope: `apikey:{projectId}`) |
| 한도 | 100 req/sec → 6000 RPM |
| 초과 응답 | 429 `RATE_429` + `Retry-After` 헤더 |
| 백엔드 | Redis (`RedisRateLimiter`) — RPM 슬라이딩 윈도우 |

본 명세가 단순 합의로 끝나지 않고 **실측 근거로 박제**되어야 발표·이력서·면접에 인용 가능.

### 시스템 구성 (Before baseline)

| 컴포넌트 | 구성 |
|---|---|
| API 서버 | Spring Boot 3.x, butterfly-api 모듈 (Jib container) |
| 인증 필터 | `ApiKeyAuthenticationFilter` (S14P31B101-205, 김동현) — pk_live_ 포맷 검증 + SHA-256 + DB 조회 |
| SPI 어댑터 | `ApiKeyValidatorImpl` (butterfly-tenant) — `ProjectRepository.findByPublicApiKey(sha256)` |
| 도메인 검증 | `UserActionIngestService` — `resolvedProjectId` vs `parent.projectId` 비교 (193 도메인 검증 머지본 c2a8533) |
| DB | MySQL 8.x, `projects.public_api_key` UNIQUE 인덱스, `notification_logs` PK 조회 |
| 레이트 리밋 | `RedisRateLimiter` (sliding window, RPM 기반) |

### Code Freeze 정합성

- D-4 (5/15 Code Freeze)
- DoD: 발표 자료 "성과 측정" 축 근거 → 본 카드가 핵심 증거 소스 1
- 5/22 발표 D-11

## Task (목표)

1. **Before baseline 박제** — 측정 환경·시드 상태·코드 commit·예상값을 카드에 미리 정착
2. **5/13 QA Run #1 에서 LT-01 실측** ([backend/docs/qa/03-load-and-failure-scenarios.md §2.1](../../backend/docs/qa/03-load-and-failure-scenarios.md#21-시나리오-lt-01--이벤트-수집-api-단순-부하-adr-0017-verification))
3. **결과 카드 승격** — 본 카드의 Result 섹션 채움, status BEFORE-ONLY → COMPLETE
4. **병목 식별 시** — 최적화 PR 머지 후 재측정 → 별도 카드 `event-ingest-api-p99-optimized` 분리 (Before/After 1쌍)

## Action (조치)

### Before 증거 — 측정 전 상태 박제

#### 코드 commit 기준

- `git rev-parse origin/develop` = **`16f53f3`** (2026-05-11 본 카드 작성 시점)
- 인증 필터: `butterfly-api/src/main/java/com/b101/butterfly/config/security/ApiKeyAuthenticationFilter.java` (75d92af 머지본)
- 도메인 검증: `butterfly-reporting/src/main/java/com/b101/butterfly/reporting/event/service/UserActionIngestService.java` (c2a8533 머지본)

#### 측정 도구·환경

| 항목 | 값 (예정) | 비고 |
|---|---|---|
| 도구 | k6 v0.49+ | brew/choco 설치 |
| 환경 | dev | docker-compose.dev.yml |
| 시드 | Project A 1개, API Key 1개, notification_logs 1 row (logId=42) | seed-load-test-fixtures.sh (별도 PR) |
| 부하 패턴 | constant-arrival-rate 100/sec, 5분 지속 | k6 scenario `sustained_100rps` |
| 임계치 | `http_req_duration{p(99)} < 200`, `http_req_failed < 0.001` | ADR-0017 명세 |

#### 예상값 (가설)

| 지표 | 예상값 | 근거 |
|---|---|---|
| p50 | 15~30ms | 인덱스 lookup + 단순 INSERT 1건 |
| p95 | 50~100ms | Redis 레이트 리밋 RTT + JVM GC 간헐 |
| p99 | **120~180ms** | ADR 명세값 200ms 마진 안 — 통과 예상 |
| failure rate | < 0.05% | 정상 요청만 발행, validation 실패 가능성 적음 |
| API 서버 CPU | 40~60% | Spring Boot + DB I/O |
| MySQL connections | 5~10 (풀 20 한도 미만) | HikariCP 기본 |

**예상이 빗나가는 케이스**:
- p99 > 200ms — 의심 1순위: `ProjectRepository.findByPublicApiKey` 인덱스 미적용 (해시 컬럼이 unique key 인지 V2 마이그레이션 재확인)
- p99 > 500ms — 의심 2순위: `RedisRateLimiter` sliding window 알고리즘이 매 호출마다 ZRANGE → 비용 큰 가능성
- failure rate > 1% — Tomcat thread pool 한도 또는 connection refused

### 적용된 변경 (Before 시점)

본 카드는 **측정 전 baseline 박제**이므로 코드 변경 없음. 단, 박제를 가능하게 한 머지 이력:

| 머지 | 의미 |
|---|---|
| `f46ce83` (193 SPI 1차) | SPI/어댑터 정의 — 이후 cleanup |
| `75d92af` (205 인증 필터) | 김동현이 별도 SPI + 필터 구현 |
| `e442c67` (193 RemoveDeadSpi + ADR-0017) | dead code 정리 + ADR 정착 |
| `c2a8533` (193 DomainValidation) | UserActionIngestService 의 resolvedProjectId 비교 가드 |
| `4a03bc0` (LogPolicyApiKeyMasking) | X-API-Key 헤더 마스킹 규칙 강제 |

이 5건이 측정 대상 흐름의 코드 표면을 확정한 상태에서 첫 측정이 일어난다.

## Result (성과)

> ⏳ **측정 대기 중** — 5/13 QA Run #1 에서 채움. 양식:

### 정량 비교 표 (TBD)

| 지표 | Before (예상) | Before (실측) | 기준 (ADR-0017) | 판정 |
|---|---|---|---|---|
| p50 | 15~30ms |   | — |   |
| p95 | 50~100ms |   | — |   |
| p99 | 120~180ms |   | **< 200ms** |   |
| failure rate | < 0.05% |   | **< 0.1%** |   |
| API 서버 CPU peak | 40~60% |   | < 80% |   |
| MySQL connections peak |   |   | < 20 |   |

### 트레이드오프 (TBD)

- ADR-0017 § "Negative" 의 "DB 직접 조회 latency 영향 미미" 가설이 검증되는지
- Redis 캐시 미사용으로 인한 비용 vs 즉시 반영의 이득 — 측정값으로 가시화
- 다른 트레이드오프 (메모리 / 복잡도) 측정값 결과로 추가 식별

### 병목 식별 (있을 때만, TBD)

- EXPLAIN / 프로파일러 출력은 `_evidence/2026-05-13_event-ingest-api-p99-explain.txt` 같이 박제
- 식별 시 후속 최적화 PR → 별도 카드로 Before/After 1쌍 박제

## Reflection (회고)

> ⏳ 측정 후 채움. 예상값 정확도(예상 p99 150ms vs 실측 145ms — 정확) 자체가 학습 자료.

## References

### 시나리오 정의
- [backend/docs/qa/03-load-and-failure-scenarios.md §2.1](../../backend/docs/qa/03-load-and-failure-scenarios.md) — LT-01

### ADR
- [ADR-0017 SDK Public API Key 인증](../../backend/docs/adr/0017-public-api-key-authentication.md) — § "Consequences" / § "Rate Limit Policy" / § "Verification Checklist"

### 관련 Jira
- `S14P31B101-68` — 부하 테스트 시나리오 작성 (본 카드의 측정 대상 트리거)
- `S14P31B101-193` — SDK Public API Key 검증 (도메인 검증 머지본)
- `S14P31B101-205` — 인증 필터 구현 (김동현)

### 코드
- `backend/butterfly-api/src/main/java/com/b101/butterfly/config/security/ApiKeyAuthenticationFilter.java`
- `backend/butterfly-tenant/src/main/java/com/b101/butterfly/tenant/adapter/ApiKeyValidatorImpl.java`
- `backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/event/service/UserActionIngestService.java`

### 증거 보존 (`docs/optimizations/_evidence/`, TBD)
- `2026-05-13_event-ingest-api-p99-k6.json` — k6 결과 원본
- `2026-05-13_event-ingest-api-p99-explain.txt` — DB EXPLAIN (병목 식별 시)
- `2026-05-13_event-ingest-api-p99-grafana.png` — Grafana 패널 캡처 (관측 스택 준비 후)

## 포트폴리오 변환 (5/22 발표·이력서·블로그·STAR)

### 4축 매핑
- **성과 측정** ⭐ — 본 카드의 핵심 축. ADR 명세 → 실측 → 박제의 완결된 흐름
- **멀티테넌트** — 프로젝트별 레이트 리밋 (`scope: apikey:{projectId}`) 동작 부산물 검증
- **비동기** — 직접 영향 없음 (이벤트 적재는 동기 INSERT)
- **채널 추상화** — 직접 영향 없음

### 발표 슬라이드 헤드라인 후보 (실측 후 확정)
- "**100 req/s 부하에서 p99 145ms (ADR 명세 200ms 마진 27%)**" — 가설 통과 케이스
- "**p99 350ms → 145ms (-58%)**" — 병목 식별 후 최적화 케이스 (별도 카드)

### 이력서 한 줄 (실측 후 확정)
> "Public API Key 인증 흐름의 latency SLA(p99 < 200ms @ 100 req/s)를 k6 부하 테스트로 1차 검증 후 [측정값 + commit hash] 박제, ADR-0017 § Verification Checklist 충족."

### STAR 답변 골격
- **S**ituation: SDK 미제공 결정 후 고객사 앱이 직접 API 호출 → SLA 합의 필요
- **T**ask: ADR-0017 명세 p99 < 200ms 가 실제 충족되는지 검증
- **A**ction: k6 부하 시나리오 정의(LT-01) + Before baseline 박제 + 측정 + 병목 식별 시 최적화
- **R**esult: 실측 p99 [TBD] ms — ADR 명세 통과/실패 판정 + 박제 위치

## 변환 후 다음 단계 (Phase 2 후보)

- 1시간 지속 부하 (5분 burst 한계 넘어)
- 1000 req/s 한도 — 현재 100 req/s 명세를 넘는 스케일 확장
- 실제 FCM/APNS 게이트웨이 부하 (Stub 어댑터 → 실 환경)
- 다른 API 의 동일 측정 (`POST /api/v1/events`, `POST /api/v1/events/batch`)

이 항목들은 5/22 발표 후 백로그로 분리.
