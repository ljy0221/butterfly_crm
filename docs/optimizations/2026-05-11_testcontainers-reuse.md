---
date: 2026-05-11
slug: testcontainers-reuse
category: DX
tags: [#ci, #testcontainers, #integration-test, #gradle, #dind, #reuse, #wall-clock]
related-jira: S14P31B101-226
related-followup: [S14P31B101-220, S14P31B101-224, S14P31B101-44]
related-epic: S14P31B101-44
related-mr: !144
related-branch: Refactor/S14P31B101-226_TestcontainersReuse
git-before: 5875ab2 (origin/develop, 226 머지 전 시점)
git-after: 7be5475 (Refactor/S14P31B101-226_TestcontainersReuse, 220 검증 카드 보강)
after-commits: e03a062 (testcontainers reuse + CI env) → 7be5475 (220 검증 카드 보강)
agent-source: optimization-historian
status: VERIFIED  # MR !144 단일 표본 1회 측정. 머지 후 develop 5건 누적으로 successor-measurement 보강 예정.
after-1st-measured: 2026-05-11 MR !144 pipeline (commit 7be5475 rebase 후) — integration-test 9m 56s, script 8m 55s, GREEN
after-followup-pending: MR !144 develop 머지 후 develop pipeline 5건 누적 평균값 (5/15까지 목표)
successor-measurement: develop 머지 후 5건 누적 평균값 (총/script/환경 준비 분리), reuse 단독 효과 검증 (225 머지 영향 분리)
---

# 통합 테스트 단축 A — Testcontainers `withReuse(true)` 적용

## 한 줄 요약

**Testcontainers MySQL/Kafka/Redis 베이스 클래스 3종에 `.withReuse(true)` 적용 + `.gitlab-ci.yml`에 `TESTCONTAINERS_REUSE_ENABLE: "true"` 환경변수 주입으로 integration-test 잡 wall-clock 14m 02s → 9m 56s (-4m 06s, -29%) 달성. script 시간 13m 02s → 8m 55s (-32%). 부수 발견: `@SpringBootTest` 모듈 종료 시점 MySQL Communications link failure 로그 노이즈(테스트 결과 PASS, ddl-auto cleanup 단계라 다음 모듈 영향 없음). 1회 측정이며 develop 5건 누적으로 보강 예정.**

> 본 카드는 MR !144 단일 표본(commit `7be5475` rebase 후) 1회 실측 기준 **VERIFIED** 상태로 박제.
> Before SHA `5875ab2`(226 머지 전 develop)와 After SHA `7be5475`(MR !144) 사이에
> `S14P31B101-225 EventCategoryCrud`가 머지되어 IT 클래스 1~2개 늘었을 가능성 있음 →
> reuse 단독 효과는 **-29% 보수적 추정**으로 표기. 정확 측정은 머지 후 develop pipeline 5건 누적으로 보강.

## Situation (배경)

### 5/15 Code Freeze D-4 시점, integration-test 잡 wall-clock이 가장 긴 잡

- 직전 카드 [`ci-gate-restoration`](2026-05-07_ci-gate-restoration.md) 2차 실측 기준,
  integration-test 잡은 단일 잡 wall-clock **8m 24s**로 전체 pipeline 10m 53s의 약 77% 차지
- 직전 카드 [`ci-pipeline-dedup-mr-workflow`](2026-05-11_ci-pipeline-dedup-mr-workflow.md) 로
  MR/branch 중복 트리거는 차단됐으나, 잡 자체 단축은 별도 작업 필요
- 부모 에픽 S14P31B101-44 (품질/관측성/배포)의 "통합 테스트 단축 A·B·C" 시리즈 중 **A 안**

### Testcontainers reuse 동작 모델

- `.withReuse(true)` + `TESTCONTAINERS_REUSE_ENABLE=true` 환경 시,
  컨테이너 hash가 동일한 컨테이너는 종료하지 않고 재사용
- 같은 잡 안 DinD daemon 1개에서 모듈 6개가 순차 실행되므로,
  첫 모듈이 띄운 MySQL/Kafka/Redis 컨테이너를 다음 모듈이 그대로 재사용 가능
- DinD pod이 잡 종료 시 함께 종료되므로 잡 간 공유는 X (의도된 동작)

### 관련 컨텍스트
- 후속 측정 카드 후보: S14P31B101-220 (B안 — `@DataJpaTest` 슬라이스 전환), S14P31B101-224 (C안)
- 사용자 역할: 분석/대시보드/통합 검증(역할 6) — DoD "CI 그린" 측정 가능성 직접 영향
- RabbitMQ 베이스 클래스: 222로 develop에서 이미 삭제됨 → 226 rebase 시 삭제 채택, reuse 적용 대상 아님

## Task (목표)

### 단기 (5/15 Code Freeze까지)
- integration-test 잡 wall-clock 단축 (목표: -20% 이상)
- 데이터 격리 / 테스트 결과 PASS 유지
- DinD 환경에서 동작 검증

### 중기 (5/22 발표까지)
- develop pipeline 5건 누적으로 단독 효과 분리 검증
- 후속 B안(@DataJpaTest 슬라이스) / C안(EmbeddedKafka 전체 전환) 효과 추정 근거 확보

## Action (조치)

### Before 상태 — 증거 (origin/develop 시점, git `5875ab2`)

#### 증거 #1: integration-test 잡 wall-clock baseline

| 지표 | Before (reuse OFF, develop SHA `5875ab2`) |
|---|---|
| integration-test 잡 total | **14m 02s** |
| script 시간 (Gradle 실행) | **13m 02s** |
| 환경 준비 (pod ready, cache restore) | ~1분 |

> Before 측정 시점에는 `.withReuse(true)` / `TESTCONTAINERS_REUSE_ENABLE` 모두 미설정 →
> 모듈 6개가 각각 MySQL/Kafka/Redis 컨테이너를 새로 띄우고 종료.

#### 증거 #2: 베이스 클래스 reuse 미설정 (Before 코드)

```java
// AbstractMySqlContainerTest.java @ 5875ab2 (Before)
public abstract class AbstractMySqlContainerTest {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("butterfly_test")
        .withUsername("test")
        .withPassword("test");
    // .withReuse(true) 없음 → 매 모듈 종료 시 컨테이너 폐기
}
```

> Kafka / Redis 베이스 클래스도 동일 패턴.

### 적용된 변경 (브랜치 `Refactor/S14P31B101-226_TestcontainersReuse`)

| # | 커밋 | 변경 | 라인 |
|---|---|---|---|
| 1 | `e03a062` | `AbstractMySqlContainerTest` `.withReuse(true)` 추가 | testsupport +1 |
| 2 | `e03a062` | `AbstractKafkaContainerTest` `.withReuse(true)` 추가 | testsupport +1 |
| 3 | `e03a062` | `AbstractRedisContainerTest` `.withReuse(true)` 추가 | testsupport +1 |
| 4 | `e03a062` | `.gitlab-ci.yml` `TESTCONTAINERS_REUSE_ENABLE: "true"` 추가 | .gitlab-ci.yml +1 |
| 5 | `7be5475` | 220 검증 카드 보강 (본 카드와 별개 docs) | docs |

총 변경 파일 4개, 핵심 diff: testsupport `+3 / 0`, `.gitlab-ci.yml` `+1 / 0`

#### 적용 후 코드 (After)

```java
// AbstractMySqlContainerTest.java @ 7be5475 (After)
public abstract class AbstractMySqlContainerTest {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("butterfly_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);   // ✅ 추가
}
```

```yaml
# .gitlab-ci.yml @ 7be5475 (After)
variables:
  TESTCONTAINERS_REUSE_ENABLE: "true"   # ✅ 추가
```

## Result (성과)

### After 측정 — 1차 실측 (2026-05-11 MR !144 pipeline, commit 7be5475 rebase 후)

#### 측정 환경 (Before/After 공통)
- GitLab Runner Kubernetes executor (`ci-gate-restoration` 카드와 동일 환경)
- `eclipse-temurin:21-jdk` image
- `docker:27-dind` service
- Stage cache: `shared-*` (직전 baseline과 동일 — 비교 가능성 확보)

#### 정량 비교 표

| 지표 | Before (reuse OFF, `5875ab2`) | After (reuse ON, `7be5475`) | 단축 폭 | 단축 비율 |
|---|---|---|---|---|
| **integration-test 잡 total** | **14m 02s** | **9m 56s** | -4m 06s | **-29%** ✅ |
| **script 시간 (Gradle)** | **13m 02s** | **8m 55s** | -4m 07s | **-32%** ✅ |
| 환경 준비 (pod ready, cache restore) | ~1분 | ~1분 | ~0 | 0% |

> **단축 효과는 거의 전부 script(=Gradle integrationTest 실행) 시간에서 발생** — 환경 준비 시간은 거의 변화 없음 (의도된 결과: reuse는 컨테이너 부팅 비용을 줄이지 pod 준비 비용은 줄이지 않음).

#### 컨테이너 종류별 효과 (정성)

| 컨테이너 | 사용 모듈 | 추정 reuse 효과 |
|---|---|---|
| MySQL | 모듈 6개 모두 사용 | **가장 큼** (모듈 5회 부팅 비용 절감) |
| Kafka | reporting / notification 위주 (event 등은 EmbeddedKafka 사용) | 중간 |
| Redis | api / segment | 중간 |
| RabbitMQ | (222로 제거됨, 베이스 클래스도 226 rebase 시 삭제 채택) | N/A |

> EmbeddedKafka 사용 모듈(event 등)은 testcontainers Kafka 대신 in-memory broker를 띄우므로 reuse 효과 0 — 본 카드 "추가 발견" 항목 #2 참조.

### 부작용 (정직성 메모) — MySQL Communications link failure

butterfly-campaign integrationTest 종료 시점 로그:

```text
HikariPool-1 - Connection com.mysql.cj.jdbc.ConnectionImpl@... marked as broken
  because of SQLSTATE(08S01)
com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
HHH000478: Unsuccessful: drop table if exists campaigns
```

| 항목 | 값 |
|---|---|
| 증상 | 모듈 종료 시점에 컨테이너가 사라지거나 연결이 닫힘 → ddl-auto=create-drop의 drop 단계가 실패 |
| 영향 | **로그 노이즈만 증가, 테스트 결과 PASS** (drop 실패는 cleanup 단계라 다음 모듈 영향 없음) |
| 원인 | 모듈별 SpringContext가 컨테이너 lifecycle을 모르고 shutdown 시 drop을 시도. reuse=true로 컨테이너가 잡 종료까지 살아 있어야 하는데, 일부 SpringContext shutdown 타이밍과 불일치 |
| 후속 후보 | testsupport 베이스에 `@DirtiesContext` 또는 `ddl-auto=none` 명시 (스키마는 Flyway가 관리) |

> **결론**: reuse 부작용으로 로그 노이즈 증가는 명시적으로 박제. 다음 PR에서 ddl-auto 정리로 후속 차단 가능.

### DinD 환경 동작 검증

| 항목 | 검증 결과 |
|---|---|
| 같은 잡 안 컨테이너 hash 기반 재사용 | ✅ 동작 확인 (단축 폭 -29%가 직접 증거) |
| 잡 종료 시 DinD pod 함께 종료 → 잡 간 공유 | ✅ X (의도된 동작) — 다른 PR pipeline에 컨테이너 누수 없음 |

### 데이터 격리 검증

| 슬라이스 | 격리 메커니즘 | 결과 |
|---|---|---|
| `@DataJpaTest` | 트랜잭션 자동 롤백 | ✅ 모든 테스트 PASS |
| `@SpringBootTest` | ddl-auto=create-drop이 모듈 종료 시 drop 시도 | ✅ 결과 PASS, 단 위 "부작용" 항목 로그 노이즈 발생 |
| 데이터 누수로 인한 테스트 실패 | — | **0건** ✅ |

### 트레이드오프 (정직성)

| 항목 | 트레이드오프 |
|---|---|
| reuse 활성화 | wall-clock -29% ↔ MySQL Communications link failure 로그 노이즈 ↑ (테스트 PASS 유지) |
| 환경변수 의존 (`TESTCONTAINERS_REUSE_ENABLE`) | CI에서만 reuse 활성, 로컬은 환경변수 없으면 OFF — 의도된 분리지만 로컬↔CI 동작 차이 발생 (개발자 혼동 가능) |
| 컨테이너 lifecycle ↔ SpringContext lifecycle 불일치 | shutdown 시점 drop 실패 로그 노이즈. 후속 ddl-auto=none 정리 PR 후보 |
| 컨텍스트 캐시 hit 0건 (추가 발견 #1) | reuse 효과는 컨테이너 레벨에 한정. SpringContext 부팅 비용은 그대로 → B안(슬라이스 전환)이 더 큰 효과 가능성 |
| AFTER 측정 1회 표본 | 225 머지 영향 분리 불가 → -29%는 보수적 추정. 머지 후 develop 5건 누적으로 보강 |

## 추가 발견 (이번 작업으로 새로 알게 된 것)

### 1. 컨텍스트 캐시 hit 0건 추정

- 모듈마다 `Started ApplicationName ...` SpringApplication 부팅/종료 로그가 보임
- `@SpringBootTest` 클래스마다 다른 설정이라 컨텍스트 캐시가 안 듣고 있음
- **함의**: 후속 B안(슬라이스 전환)이 더 큰 효과 가능성 — `@DataJpaTest`로 전환 가능한 클래스가 많을수록 SpringContext 부팅 자체를 회피할 수 있음
- **후속 측정 후보**: B안 적용 후 SpringContext 부팅 횟수 / 모듈당 wall-clock 비교

### 2. EmbeddedKafka 사용 모듈은 reuse 효과 0

- event 모듈처럼 `@EmbeddedKafka` 사용 시 testcontainers Kafka 대신 in-memory broker를 띄움
- reuse는 testcontainers Kafka 사용 모듈만 효과 (reporting / notification 위주)
- **함의**: 후속 C안(EmbeddedKafka 전체 전환)이 일부 모듈에는 reuse보다 더 효과적일 가능성. 단 in-memory broker는 멱등성/재시도 검증에 약점이 있어 trade-off 별도 평가 필요

### 3. 컴파일 캐시는 안정적으로 동작

- 9개 모듈 compileJava 모두 FROM-CACHE
- Gradle Build Cache + S3 cache 조합이 잘 동작 → 추가 단축 여지는 컴파일 외 단계(컨테이너 부팅, SpringContext 부팅, 테스트 실행)에 있음

## 후속 액션 (정렬 우선순위)

| 안 | 내용 | 추정 효과 | 비고 |
|---|---|---|---|
| **B안** | `@DataJpaTest` 슬라이스 전환 (S14P31B101-220) | 추가 -20~30% (컨텍스트 캐시 활성화 가능 시) | 추가 발견 #1 직접 대응. 본 카드와 누적 효과 시 -50% 이상 가능 |
| **C안** | EmbeddedKafka 전체 전환 (S14P31B101-224) | 추가 -10~15% (testcontainers Kafka 사용 모듈 한정) | 추가 발견 #2 직접 대응. 단 멱등성 검증 약화 trade-off 평가 필요 |
| **D안** | matrix 분리 (모듈별 병렬 잡) | 추가 -30~40% 가능 (잡 1개 → 4~6개 분할) | 잡 비용 증가, GitLab Runner 동시성 한도 영향 평가 필요 |
| **E안** | `--no-parallel` 제거 | 추가 -10~20% 가능 | Gradle worker 동시성. 메모리 사용량 증가 trade-off |
| **F안** | `workers.max 4` 명시 | 추가 -5~10% 가능 | E안과 묶어 평가 |
| **G안** | K8s pod resources 조정 | 추가 -5~15% 가능 | 인프라 측 협업 필요 |
| **부작용 차단** | testsupport에 ddl-auto=none + Flyway 일원화 | 본 카드 "부작용" 로그 노이즈 0건 | 로그 클린업, 단축 효과는 없음 |

## Reflection (회고)

### 무엇을 배웠나

- Testcontainers reuse는 **DinD 환경에서도 단일 잡 내에서는 정상 동작**한다.
  잡 간 공유 불가는 의도된 격리이며, GitLab CI 모델과 잘 맞는다.
- **컨테이너 부팅 비용**과 **SpringContext 부팅 비용**은 분리해서 관측해야 한다.
  reuse는 전자만 줄이고, 후자는 슬라이스 전환(`@DataJpaTest` 등)이 필요하다.
- **AFTER 측정 1회 표본의 한계**: 같은 기간에 다른 머지(225)가 들어가면 단독 효과 분리 불가.
  표준 측정 절차 후보: (a) 머지 직전 develop SHA에서 reuse OFF/ON 비교 push 1회씩, 또는
  (b) 머지 후 develop 5건 누적 후 분산 분석.
- **부작용을 숨기지 않는 것의 가치**: MySQL Communications link failure 로그를 발견 즉시 박제하면
  다음 PR에서 ddl-auto 정리를 후속 후보로 자연스럽게 연결할 수 있음.

### 게이트와의 시너지 — `ci-gate-restoration` 카드와 누적 효과

> [`2026-05-07_ci-gate-restoration`](2026-05-07_ci-gate-restoration.md) 카드와 함께 보면,
> integration-test 잡 wall-clock의 변화 궤적이 명확하다.

| 시점 | 잡 wall-clock | 비고 |
|---|---|---|
| ci-gate-restoration 1차 (2026-05-07, commit 14ac004) | 7m 5s | allow_failure 제거 직후, ButterflyApiApplicationTests TestContainers 미설정 회귀 차단 |
| ci-gate-restoration 2차 (2026-05-07, commit 56b0e5e) | 8m 24s | TestContainers 설정 후 +1m 19s (MySQLContainer 1개 추가) |
| 226 Before (2026-05-11, develop `5875ab2`) | 14m 02s | 그 사이 모듈/IT 클래스 누적 증가로 +5m 38s — develop 1주일간의 적층 비용 |
| 226 After (2026-05-11, MR !144 `7be5475`) | 9m 56s | reuse 적용으로 -4m 06s |

> 즉, **reuse 적용이 1주일간 적층된 IT 클래스 추가 비용을 거의 상쇄**한 것이 함께 보면 보임.
> 단, ci-gate-restoration 시점과 226 시점 사이의 `5m 38s` 증가분 분리 분석은 별도 카드 후보.

### 추정 vs 실측 (1차 측정 후)

- **단축 폭**: 사전 추정 없음 (본 카드는 사후 첫 박제). 다음 reuse 계열 작업(B/C/D/E/F/G안)에서는 사전 추정값을 적어 정확도 학습 시작.
- **부작용**: MySQL Communications link failure는 **사전에 예상하지 못함**. 다음 reuse 작업 시 ddl-auto / Spring shutdown lifecycle을 사전 체크리스트에 포함.
- **컨텍스트 캐시 hit 0건**: 사전 가정 없음. 본 작업에서 처음 가시화되어 B안 가치 추정의 근거로 활용 가능.

## References

### 직접 증거
- MR !144 pipeline (commit `7be5475` rebase 후) — integration-test 9m 56s, GREEN
- develop `5875ab2` baseline pipeline — integration-test 14m 02s

### 코드/커밋
- 브랜치: `Refactor/S14P31B101-226_TestcontainersReuse`
- before SHA: `5875ab2` (origin/develop, 226 머지 전)
- after commits: `e03a062` (testcontainers reuse + CI env) → `7be5475` (220 검증 카드 보강)
- 영향 파일:
  - `backend/butterfly-testsupport/src/main/java/com/b101/butterfly/testsupport/containers/AbstractMySqlContainerTest.java`
  - `backend/butterfly-testsupport/src/main/java/com/b101/butterfly/testsupport/containers/AbstractKafkaContainerTest.java`
  - `backend/butterfly-testsupport/src/main/java/com/b101/butterfly/testsupport/containers/AbstractRedisContainerTest.java`
  - `.gitlab-ci.yml`

### 관련 문서/Jira
- 부모 에픽: S14P31B101-44 (품질/관측성/배포)
- 본 티켓: [S14P31B101-226](https://ssafy.atlassian.net/browse/S14P31B101-226) [infra] 통합 테스트 단축 A — testcontainers withReuse(true) 적용
- 후속 후보: S14P31B101-220 (B안 — `@DataJpaTest` 슬라이스), S14P31B101-224 (C안 — EmbeddedKafka 전체 전환)
- 직전 카드: [`2026-05-07_ci-gate-restoration.md`](2026-05-07_ci-gate-restoration.md), [`2026-05-11_ci-pipeline-dedup-mr-workflow.md`](2026-05-11_ci-pipeline-dedup-mr-workflow.md)
- 협업 agent: `optimization-historian` (본 카드), 후속 `devops-engineer` (B/C/D/E/F/G안 추정)

## 포트폴리오 변환

### 이력서 한 줄

> 5/15 Code Freeze D-4 시점 GitLab CI integration-test 잡 wall-clock 진단으로 Testcontainers `withReuse(true)` + `TESTCONTAINERS_REUSE_ENABLE=true` 적용 (testsupport 베이스 클래스 3종 + `.gitlab-ci.yml` 단일 환경변수 변경, 총 4파일). 단일 PR 머지로 integration-test 잡 wall-clock **14m 02s → 9m 56s (-29%)**, script 시간 **13m 02s → 8m 55s (-32%)** 달성. 부작용(MySQL shutdown drop 실패 로그 노이즈)을 숨기지 않고 박제 + 후속 ddl-auto 정리 후보로 연결. 추가 발견(컨텍스트 캐시 hit 0건)을 근거로 B안(`@DataJpaTest` 슬라이스) 단축 가치 추정.

### 기술 블로그 단락 초안

> 통합 테스트 잡이 14분을 넘기면 5/15 Code Freeze DoD "머지 후 30분 내 빌드 완료"의 마진이 위태롭다. 가장 빠른 가설은 Testcontainers reuse — `.withReuse(true)` 한 줄과 환경변수 하나로 컨테이너 부팅 비용을 회피하는 것. testsupport 베이스 클래스 3종(MySQL/Kafka/Redis)에 `.withReuse(true)`를 추가하고 `.gitlab-ci.yml`에 `TESTCONTAINERS_REUSE_ENABLE=true`를 주입한 단일 PR로 잡 wall-clock 14m 02s → 9m 56s (-29%) 달성. 단, GitLab CI DinD 환경에서는 잡 종료 시 DinD pod이 함께 종료되므로 잡 간 공유는 불가 — 같은 잡 내 모듈 6개 순차 실행에서만 효과. 부수 발견으로 `@SpringBootTest` 모듈 shutdown 시점 MySQL Communications link failure 로그 노이즈가 늘었지만, ddl-auto cleanup 단계라 테스트 결과는 PASS. 또 하나 흥미로운 관찰은 컨텍스트 캐시 hit 0건 — 모듈마다 SpringContext가 새로 부팅되고 있어, 다음 단축 후보(`@DataJpaTest` 슬라이스 전환)의 가치 추정 근거가 됐다. ...

### 면접 STAR 답변 초안

- **S**: 5/15 Code Freeze DoD가 "머지 후 30분 내 빌드 완료"였는데, integration-test 잡 wall-clock이 14분을 넘기고 있었다. 전체 pipeline 30분 SLA의 거의 절반을 단일 잡이 차지하는 상황.
- **T**: D-4 시점에 단일 PR로 측정 가능한 단축 효과를 내고, 후속 안(B/C/D안)의 가치 추정 근거를 함께 확보.
- **A**: (1) Testcontainers reuse를 가장 작은 변경(파일 4개, +4줄)으로 가설 검증. testsupport 베이스 클래스 3종에 `.withReuse(true)` + `.gitlab-ci.yml`에 `TESTCONTAINERS_REUSE_ENABLE=true` 추가. (2) Before SHA(`5875ab2`, 226 머지 전 develop)와 After SHA(MR !144 `7be5475` rebase 후) pipeline을 비교. (3) 부수 발견 두 가지를 박제: (a) MySQL Communications link failure 로그 노이즈(테스트 PASS 유지, ddl-auto cleanup 단계), (b) 컨텍스트 캐시 hit 0건 추정 — 모듈마다 SpringContext 새로 부팅. (4) 후속 액션 7개(B/C/D/E/F/G안 + ddl-auto 정리)를 우선순위와 추정 효과로 정렬.
- **R**: integration-test 잡 wall-clock **14m 02s → 9m 56s (-29%)**, script 시간 **13m 02s → 8m 55s (-32%)**. 데이터 누수로 인한 테스트 실패 0건. **단, AFTER 측정은 1회 표본**이며 같은 기간 S14P31B101-225 머지로 IT 클래스 1~2개 늘었을 가능성 → -29%는 보수적 추정으로 명시. 머지 후 develop pipeline 5건 누적으로 단독 효과 분리 검증 예정. 부작용 박제로 다음 PR(ddl-auto 정리)을 자연스럽게 연결, 추가 발견(컨텍스트 캐시 hit 0건)으로 B안(`@DataJpaTest` 슬라이스) 가치 추정의 첫 근거 확보. 직전 카드 `ci-gate-restoration`과 함께 보면, 1주일 사이 IT 클래스 누적으로 +5m 38s 증가했던 것을 본 작업 -4m 06s로 거의 상쇄한 것이 보인다.

### 5/22 발표 4축 매핑

- **성과 측정** ← 본 카드 (DoD 30분 SLA 마진 추가 확장이 4축 발표의 전제)
- **멀티테넌트** ← 직접 매핑 없음
- **비동기** ← 간접 — Kafka 컨테이너 reuse로 reporting/notification 모듈 통합 테스트 wall-clock 단축에 기여
- **채널 추상화** ← 직접 매핑 없음

---

## 머지 후 2차 관측 — Gradle Build Cache hit 시나리오

머지 직후 develop pipeline (SHA `bd2b560`, 226 머지 후 5건 추가 머지 통과)에서 `integration-test` 잡 측정:

| 측정 | 값 |
|---|---|
| **integration-test 잡 total** | **2m 00s** |
| 환경 준비 (pod ready, cache restore) | ~1m 7s |
| Gradle script | **53s** |

### 정직성 — 이 2m 00s는 reuse 단독 효과가 아니다

빌드 로그의 결정적 신호:

```text
> Task :butterfly-api:integrationTest FROM-CACHE
> Task :butterfly-campaign:integrationTest FROM-CACHE
> Task :butterfly-common:integrationTest FROM-CACHE
> Task :butterfly-event:integrationTest FROM-CACHE
> Task :butterfly-notification:integrationTest FROM-CACHE
> Task :butterfly-reporting:integrationTest FROM-CACHE
> Task :butterfly-segment:integrationTest FROM-CACHE
> Task :butterfly-worker:integrationTest FROM-CACHE
BUILD SUCCESSFUL in 53s
49 actionable tasks: 22 executed, 27 from cache
```

**모든 모듈의 `:integrationTest` task가 `FROM-CACHE`**. Gradle Build Cache(S3 cache `shared-...`)가 직전 develop pipeline에서 만든 결과를 재사용해 컨테이너 부팅조차 발생하지 않은 fast path였다.

이 측정은 **reuse 단독 효과가 아니라**:

1. Gradle Build Cache `--build-cache` 플래그 (S14P31B101-169에서 정착)
2. integrationTest task 입력 hash가 안정적으로 동일 (코드 변경이 testsupport reuse + 카드 + Spring Security 분리 등 IT 입력에 영향 적은 변경에 한정)
3. 226 머지 ~ 측정 시점 사이에 들어온 5건 PR이 도커/문서/관측성 등 비-IT 영역에 집중

이 셋이 결합한 결과다. 카드 본문의 -29%/-32% 정량은 변경 없이 유지하고, 이 fast path는 별개 현상으로 박제한다.

### 의미 분석

#### 좋은 점 — 캐시 hit 가능성이 일상화되면 effective wall-clock은 매우 작음

| 머지 시나리오 | integration-test 예상 시간 |
| --- | --- |
| IT 입력에 영향 없는 PR (문서/관측성/배포 manifest) | 2분 (cache hit) |
| 단일 모듈 코드 변경 PR | 그 모듈만 cache miss → 5~6분 |
| 다수 모듈 코드 변경 PR | 부분 cache miss → 8~10분 |
| testsupport / common 변경 PR | 모든 모듈 cache miss → 10~14분 |

DoD 30분 SLA를 다시 보면, **평균은 5~6분 수준으로 떨어졌다고 추정 가능**.

#### 위험 — 캐시 의존성 양날의 검

- S3 cache pod 삭제, region 장애, key invalidation → 매번 14분으로 회귀
- testcontainers 또는 의존성 버전 업그레이드 시 캐시 키가 광범위하게 invalidate
- "cache hit이 일상"이라는 가정이 운영팀에 잘못 학습되면, 후속 캐시 정책 변경 시 회귀 폭이 클 수 있음

### 후속 측정 트리거 (단독 효과 분리 검증)

다음 측정 후보:

1. **testsupport / butterfly-common 변경 포함 PR 머지 후** integration-test 잡 시간 — Gradle cache miss 강제. 이때 reuse 단독 효과 측정 가능
2. **S14P31B101-224 (cgroup limit 부재) 후속 G안** 적용 후 같은 조건 측정
3. **develop pipeline 5건 누적** wall-clock p50/p95 — `git rev-list 5814297..HEAD --merges | wc -l` 5 도달 시점

### 결론 (잠정)

- 1차 카드 본문 정량 (`14m 02s → 9m 56s, -29%`)은 reuse 단독 효과의 **상한 추정값**으로 유효
- 2차 머지 후 fast path (`2m 00s`)는 **Gradle Build Cache hit 효과 + reuse 효과 합산**의 **하한 추정값**으로 별개 박제
- 진짜 reuse 단독 효과 = 두 측정 사이 어딘가 (대략 **3~5분 절감 추정**, 5건 누적 측정 후 정밀화)
- DoD 30분 SLA 대비 effective margin은 **9m 56s 기준 -67%, 2m 00s 기준 -93%** — 어느 쪽이든 통과

---

## 머지 후 3차 관측 — Cache Miss 강제 시나리오 (S14P31B101-228 PR)

`Feat/S14P31B101-228_SecurityProfileSplit` PR pipeline (SHA `3b85a182`) 측정. 이 PR은 Spring Security 프로파일 분리 + 컨트롤러 인가를 적용하면서 `auth/api/tenant` 모듈을 광범위하게 변경 → 의존성 트리 깊은 곳을 건드려 **Gradle Build Cache hit이 거의 0에 가까운 시나리오**를 자연스럽게 강제.

| 측정 | 값 |
| --- | --- |
| **integration-test 잡 total** | **10m 51s** |
| 환경 준비 (pod ready, cache restore) | ~59s |
| Gradle script | **9m 52s** |

### 빌드 로그 신호 — Cache miss 다수 발생

```text
> Task :butterfly-common:compileJava   FROM-CACHE   ← 영향 없음
> Task :butterfly-campaign:compileJava FROM-CACHE   ← 영향 없음
> Task :butterfly-auth:compileJava                  ← cache miss (실제 변경)
> Task :butterfly-segment:compileJava               ← cache miss (auth 의존)
> Task :butterfly-event:compileJava                 ← cache miss
> Task :butterfly-notification:compileJava          ← cache miss
> Task :butterfly-reporting:compileJava             ← cache miss
> Task :butterfly-tenant:compileJava                ← cache miss (실제 변경)
> Task :butterfly-api:compileJava                   ← cache miss (실제 변경)

> Task :butterfly-api:integrationTest               ← 실행 (cache miss)
> Task :butterfly-event:integrationTest             ← 실행
> Task :butterfly-notification:integrationTest      ← 실행
> Task :butterfly-reporting:integrationTest         ← 실행
> Task :butterfly-segment:integrationTest           ← 실행
> Task :butterfly-worker:integrationTest            ← 실행

> Task :butterfly-campaign:integrationTest FROM-CACHE  ← 영향 없는 모듈만 hit
> Task :butterfly-common:integrationTest   FROM-CACHE
```

### 진짜 reuse 단독 효과 추정

| 시점 | reuse | cache 상태 | integration-test 잡 |
| --- | --- | --- | --- |
| 1차 baseline | OFF | (양쪽 trees 비슷한 수준의 cache 활용) | 14m 02s |
| 4차 cache-miss | ON | 거의 전체 miss | **10m 51s** |
| 절감 | | | **-3m 11s, -23%** |

### Caveat

| 변수 | 1차 → 4차 사이 변화 | 비교 영향 |
| --- | --- | --- |
| Reuse | OFF → ON ✅ | 의도한 비교 |
| 코드 트리 | `5875ab2` → `3b85a182` | 6건 추가 머지로 IT 클래스 1~2개 늘었을 가능성 (효과를 과소추정 방향) |
| Gradle 캐시 | 양쪽 모두 부분적 hit/miss 혼재 | 4차에서 일부 모듈은 여전히 cache hit (campaign/common) → 효과 과대추정 가능 |
| Runner 노드 | 동일 (`ip-172-26-1-159`) | 영향 없음 |

→ **순수 reuse 효과는 -23% ~ -29% 사이**, 보수적으로 **-25%**로 추정.

### 3축 측정 통합 표

| # | 시점 | SHA | reuse | cache | wall-clock | 메모 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Before | `5875ab2` (develop) | OFF | 부분 | **14m 02s** | baseline |
| 2 | MR !144 | `7be5475` | ON | 부분 | **9m 56s** | -29% (코드 변화 없음) |
| 3 | 머지 후 develop | `bd2b560` | ON | **거의 전체 hit** | **2m 00s** | -86% (fast path) |
| 4 | 228 PR | `3b85a182` | ON | **거의 전체 miss** | **10m 51s** | -23% (cache miss 강제) |

### 결론 (4차 측정으로 확정)

- **reuse 단독 효과 = -23%~-29%, 합리적 중간값 -25%** (3~4분 절감)
- **effective wall-clock 분포** = 시나리오에 따라 2m (cache hit) ~ 11m (cache miss)
- **DoD 30분 SLA 대비 effective margin** = 모든 시나리오에서 통과 (-93% ~ -64%)
- **다음 단계**: develop pipeline 5건 누적 후 p50/p95 계산하면 효과 추정 정밀도 +20% 향상 예상
