---
status: accepted
date: 2026-05-08
decision-makers: 역할 1 (auth/tenant) 김동현, 역할 6 (analytics) 이재영
consulted: 역할 4 (campaign) — `pk_live_` 발급 정책 참조
informed: 역할 5 (delivery) — SDK 가이드 작성 시 참조
---

# ADR-0017: SDK Public API Key 인증 — Bean SPI + DB 단일 진실 + SHA-256 해시 저장

## Context and Problem Statement

SDK(앱/웹)가 발행하는 사용자 반응 이벤트 (`POST /api/v1/events/actions` — OPEN/CLICK/CONVERT/DISMISS)는 멀티테넌트 환경에서 **다른 프로젝트의 데이터를 조작/조회하지 못하도록 인증**이 필요하다. 다음 두 가지가 동시에 충족돼야 한다:

1. **모듈 경계 (ADR-0014)**: 이벤트 도메인(`butterfly-reporting/event`)이 테넌트 도메인(`butterfly-tenant`)에 직접 의존하지 않아야 한다. SPI 패턴으로 격리.
2. **단일 진실 공급원**: API 키의 유효성·소속 프로젝트는 `tenant.projects.public_api_key` 가 최종 판단. 재발급/삭제 즉시 반영되어야 한다.

또한 보안 요건:
- 평문 키는 DB 에 저장하지 않는다 (DB 유출 시 SDK 인증 정보 노출 방지).
- SDK 가 보내는 평문 키는 서버 메모리·로그에 남기지 않는다.
- 키 형태 검증으로 명백히 잘못된 요청은 DB 조회 없이 차단.

## Considered Options

| 옵션 | 동작 | 장점 | 단점 | 결정 |
|---|---|---|---|---|
| **A. Bean SPI (Spring 인터페이스)** | reporting → SPI 인터페이스 의존, butterfly-tenant 가 구현체 등록 | 컴파일 타임 타입 안전, 동일 JVM 직접 호출(빠름), Spring 표준, `ProjectPermissionEvaluator` 와 동일 패턴 | modular monolith 가정 (마이크로서비스 분리 시 어댑터 교체) | ✅ 채택 |
| B. REST | tenant 가 `/internal/api-keys/validate` 노출, reporting 이 RestTemplate 호출 | 서비스 분리 자연스러움 | 동일 JVM self-call 비효율, 매 요청 추가 hop | ⚠️ 과함 |
| C. Kafka 토픽 | tenant 가 키 변경 이벤트 발행, reporting 이 캐시 빌드 | 비동기 | 정합성 검증은 동기 read 가 자연 — Kafka 부적합. 캐시 정합성 어려움 | ❌ |
| D. DB 직접 조회 | reporting 이 `projects` 테이블 SELECT | 가장 빠름 | 모듈 캡슐화 깨짐, schema 변경 시 reporting 도 깨짐 | ❌ ADR-0014 위반 |

## Decision Outcome

**옵션 A — Bean SPI 채택.**

### SPI 정의 (`butterfly-common/global/security/ApiKeyValidator`)

```java
public interface ApiKeyValidator {
    Optional<Long> resolveProjectId(String sha256);
}
```

- 입력: **SHA-256 해시된 키 64자** (호출자가 해싱 책임을 가짐).
- 반환: 유효 키면 projectId, 무효면 `Optional.empty()`.
- 어떤 케시 레이어도 사용하지 않음 — DB 직접 조회. 재발급/삭제 즉시 반영.

### 구현체 (`butterfly-tenant/adapter/ApiKeyValidatorImpl`)

```java
public Optional<Long> resolveProjectId(String sha256) {
    return projectRepository.findByPublicApiKey(sha256)
            .map(Project::getId);
}
```

- 어댑터 정적 메서드 `isValidFormat(String plainKey)` — `pk_live_` 접두사 + 32자 길이 검증. 인증 필터가 해싱 전 호출해 명백한 잘못된 요청 차단.

### 인증 필터 (`butterfly-api/config/security/ApiKeyAuthenticationFilter`)

`/api/v1/events/**` 경로에만 적용. 처리 순서:

1. `X-API-Key` 헤더 추출
2. 헤더 없음 → 401 `AUTH_401`
3. 포맷 검증 (`pk_live_` + 32자) → 불일치 시 401 (DB 조회 회피)
4. SHA-256 해싱 후 `ApiKeyValidator.resolveProjectId` 호출
5. `Optional.empty()` → 401
6. 레이트 리밋 (`RateLimiter.tryAcquire`, scope=`apikey:{projectId}`, limit=`requestsPerSecond * 60` RPM)
7. 초과 시 429 `RATE_429` + `Retry-After` 헤더
8. 통과 시 `request.setAttribute("resolvedProjectId", projectId)` → 다음 필터로 전달

### 도메인 검증 (`UserActionIngestService`)

인증 필터가 request attribute 에 설정한 `resolvedProjectId` 와 요청 페이로드의 `logId.projectId` 가 일치하는지 비교. 불일치 시 `400 LOG_NOT_FOUND` — 다른 프로젝트 logId 의 존재 사실 자체를 노출하지 않기 위해 `403 FORBIDDEN` 이 아닌 `400 LOG_NOT_FOUND` 로 통일.

## Key Format Policy

| 항목 | 값 |
|---|---|
| 접두사 | `pk_live_` |
| 총 길이 | **32자 고정** (`pk_live_` 8자 + UUID hex 24자) |
| 구성 | 영소문자 + 숫자 (UUID hex) |
| DB 저장 | **SHA-256 해시 64자** (`Project.publicApiKey` 컬럼, `VARCHAR(100)`) |
| 발급 로직 | `ApiKeyService.generateApiKey()` (butterfly-tenant) |
| 해싱 알고리즘 | SHA-256, UTF-8 입력, hex lowercase 64자 |
| 해싱 헬퍼 | `butterfly-common/global/util/HashUtils.sha256(String)` |

## Rate Limit Policy

| 항목 | 값 | 비고 |
|---|---|---|
| 기준 단위 | 프로젝트별 (scope: `apikey:{projectId}`) | API 키별이 아닌 프로젝트별 — 여러 키 발급 시 합산 |
| 한도 | 100 req/sec → **6000 RPM** | `application.yml` 의 `butterfly.ratelimit.api-key.requests-per-second` (기본 100) |
| 초과 응답 | 429 `RATE_429` + `Retry-After` 헤더 | `RateLimiter.Decision.retryAfterSeconds()` |
| 백엔드 | Redis (`RedisRateLimiter`) | RPM 슬라이딩 윈도우 |

## Consequences

### Positive

- **모듈 경계 보존**: reporting/event 가 tenant 에 직접 의존하지 않음 — ADR-0014 부합.
- **단일 진실 공급원**: API 키 재발급/삭제가 즉시 반영. 캐시 무효화 로직 불필요.
- **포맷 가드**: 명백히 잘못된 요청은 DB 부담 없이 차단.
- **레이트 리밋**: 악의적 SDK 트래픽이 일부 프로젝트에 집중되어도 전체 시스템 영향 최소화.
- **request attribute 전달**: 인증 필터가 도메인 서비스에 projectId 를 명시적으로 넘김 — 도메인 코드가 SecurityContext에 의존하지 않음.

### Negative

- **modular monolith 전제**: 마이크로서비스로 분리 시 SPI 어댑터를 REST/gRPC 로 교체해야 함 (옵션 B 로 회귀).
- **DB 직접 조회**: 캐시가 없어 매 SDK 요청마다 1회 SELECT — 단, 인덱스(`unique key`) 사용으로 latency 영향 미미. 부하 테스트(S14P31B101-68) 에서 p99 < 200ms 확인 예정.

### Risks

- **헤더 누수**: 로그에 `X-API-Key` 가 그대로 찍히면 평문 키 노출. 본 ADR과 함께 `log-policy.md` 의 PII 마스킹 규칙에 헤더 마스킹 항목 추가 필요 (별도 PR).

## Verification Checklist

- [x] SPI + 구현체 + 필터 코드 — 75d92af 머지 (S14P31B101-205)
- [x] dead SPI 제거 — 46b8ae7 (S14P31B101-193 cleanup)
- [ ] 100 req/sec 부하 테스트 p99 < 200ms — S14P31B101-68 (예정)
- [ ] `UserActionIngestService` 도메인 검증 (resolvedProjectId vs logId.projectId) — 후속 PR
- [ ] log-policy.md PII 마스킹에 `X-API-Key` 헤더 추가 — 별도 PR

## References

- 합의 기록: [project_api_key_validator_spi.md](memory) (이재영 메모리)
- 관련 ADR: [ADR-0014](./0014-events-persisted-topic.md) 모듈 경계, [ADR-0005](./0005-auth-filter-placement.md) 인증 필터 배치
- 관련 Jira: S14P31B101-193 (SPI 설계), S14P31B101-205 (인증 필터 구현)
- 코드: `butterfly-api/.../ApiKeyAuthenticationFilter`, `butterfly-tenant/.../ApiKeyValidatorImpl`, `butterfly-common/.../HashUtils`
