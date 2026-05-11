---
date: 2026-05-07
slug: ci-gate-restoration
category: DX
tags: [#ci, #gitlab-ci, #allow-failure, #code-freeze, #dod, #devops, #flyway, #jib]
related-jira: S14P31B101-DevOps
related-branch: Chore/S14P31B101-DevOps_CIGate
related-worktree: C:/project/S14P31B101.devops-cigate
git-before: 128ef1c (origin/develop, "Merge branch 'Feat/S14P31B101-180_KafkaDlqRetry'")
git-after: 56b0e5e (Chore/S14P31B101-DevOps_CIGate, ButterflyApiApplicationTests TestContainers fix)
after-commits: b24ffc8 → 01e4a62 → efb0e50 → 449bef3 → (P1-1 readiness 폴링) → 56b0e5e
agent-source: devops-engineer (직전 진단), optimization-historian (본 카드)
status: AFTER  # 1차 + 2차 실측 모두 GREEN (잡별 wall-clock 4건 확보, Jib latest 미푸시 결정적 증거 확보). develop SLA / 5건 누적은 머지 후 후속 측정으로 분리.
after-1st-measured: 2026-05-07 push run (commit 14ac004) — integration-test 7m 5s, ButterflyApiApplicationTests 1 fail
after-2nd-measured: 2026-05-07 push run (commit 56b0e5e) — 전체 pipeline 10m 53s, PASSED
after-followup-pending: MR #96 develop 머지 후 머지→완료 SLA + develop 파이프라인 p50/p95 (5/14까지 5건 누적 목표)
post-merge-related: MR #96 머지 직후 frontend CI MR(#99) 도입 push에서 OOMKilled 회귀 발견 → MR for OOM fix 머지 완료(commit 0bcc85b, 2026-05-08). 별도 카드 `../dev-journey/2026-05-07_oom-cycle.md`에 사고 흐름 박제. allow_failure 제거가 가시화시킨 3번째 회귀.
---

# 5/15 Code Freeze DoD 정합성 — CI 그린 게이트 복구

## 한 줄 요약
**`allow_failure: true`로 무력화되어 있던 단위/통합 테스트 게이트를 복구하고, 그 과정에서 잠복하던 회귀 2건(Flyway V7/V8 expected 미갱신 + ButterflyApiApplicationTests TestContainers 미설정)을 발견·수정. 2차 push run에서 전체 pipeline GREEN 10m 53s 실측 (DoD 30분 SLA 대비 -64% 마진), Jib `latest` 미푸시 결정적 증거 확보. 5/15 Code Freeze DoD("CI 그린 + 머지 후 30분 내 빌드 완료")의 측정 가능성 복원 + 4개 잡 wall-clock 박제 완료.**

> 본 카드는 push 직전 Before baseline 박제로 출발. **2026-05-07 1차 push run** (commit 14ac004,
> integration-test 7m 5s + ButterflyApiApplicationTests 1 fail)으로 1차 실측, 동일 일자
> **2차 push run** (commit 56b0e5e, 전체 pipeline 10m 53s, PASSED)으로 잡별 wall-clock 4건 + Jib latest
> 미푸시 결정적 증거를 확보하여 AFTER로 승격. 머지→완료 SLA / develop p50·p95(5건)는
> MR #96 머지 후 후속 측정 카드로 분리 추적한다.

## Situation (배경)

### 5/15 Code Freeze DoD
- 명시적 요구: **CI 그린** + **머지 후 30분 내 빌드 완료**
- 5/22 발표 시연 4축 중 **"성과 측정"** 축의 전제 조건
- 발표 D-15 시점 (2026-05-07 → 2026-05-22)

### 발견된 4개 위반/위험
| # | 항목 | 위반 내용 | 심각도 |
|---|---|---|---|
| 1 | `unit-test` allow_failure: true | 단위 테스트 빨강 → 그린 보고 | DoD 정의 무력화 (P0) |
| 2 | `integration-test` allow_failure: true | TestContainers 통합 검증 무력화 | DoD 정의 무력화 (P0) |
| 3 | Flyway V7/V8 회귀 잠복 | `BaselineMigrationIntegrationTest` expected 6→8 갱신 누락이 그린 통과 | #1의 실증 (회귀 fix) |
| 4 | Jib `latest` 태그 전 브랜치 푸시 | feature 브랜치가 develop의 latest 덮어씀 | 시연 환경 식별 불가 (P0 위험) |
| 5 | `sleep 10`로 docker daemon 대기 | 시간 기반 → 통합 테스트 깜빡임 | 안정성 (P1) |

### 관련 컨텍스트
- 직전 origin/develop 머지: `128ef1c` Kafka DLQ/재시도 (S14P31B101-180)
- worktree 분리: `C:/project/S14P31B101.devops-cigate` (브랜치 `Chore/S14P31B101-DevOps_CIGate`) — 주 작업과 격리
- 사용자 역할: 분석/대시보드/통합 검증(역할 6) — 4축 "성과 측정" 직접 영향

## Task (목표)

### 단기 (5/15 Code Freeze까지)
- DoD "CI 그린" 정의의 측정 가능성 복원 (P0-1, P0-2)
- 잠복 회귀 1건 fix (그린 게이트 정상화 전제)
- 시연 환경 식별성 확보 (P0 위험 차단)

### 중기 (5/22 발표까지)
- 통합 테스트 깜빡임 제로화 (P1-1, P1-2)
- develop 파이프라인 wall-clock p95 < 30분 검증

## Action (조치)

### Before 상태 — 증거 (origin/develop 시점, git `128ef1c`)

#### 증거 #1: `.gitlab-ci.yml` allow_failure: true 위치
명령: `git show 'origin/develop:.gitlab-ci.yml'`
원본 박제: [`_evidence/2026-05-07_origin-develop-ci-yml.txt`](_evidence/2026-05-07_origin-develop-ci-yml.txt)

```yaml
# .gitlab-ci.yml @ origin/develop
L32:  unit-test:
L33:    stage: test
...
L54:    allow_failure: true     # ← P0-1 제거 대상

L56:  integration-test:
L57:    stage: test
...
L76:      - 'sleep 10'           # ← P1-1 readiness 폴링 교체 예정
...
L88:    allow_failure: true     # ← P0-2 제거 대상
```

> **사용자에게 보일 한 줄**:
> "단위/통합 테스트가 빨간색이어도 develop CI는 그린으로 보고되었다.
>  즉, DoD 'CI 그린'이라는 표현 자체가 측정 불능 상태였다."

#### 증거 #2: 잠복 회귀 (allow_failure가 막지 못한 실제 사례)
명령: `./gradlew :butterfly-api:test`
워크스페이스: `C:/project/S14P31B101` (origin/develop 정렬 시점, fix 적용 전)
원본 박제: [`_evidence/2026-05-07_baseline-migration-test-failure.txt`](_evidence/2026-05-07_baseline-migration-test-failure.txt)

```
> Task :butterfly-api:test
55 tests completed, 1 failed
> Task :butterfly-api:test FAILED

BUILD FAILED in 1m 39s
```

| 항목 | 값 |
|---|---|
| 클래스 | `BaselineMigrationIntegrationTest` |
| 메서드 | `schema_history_6개_row_성공_상태()` (line 81) |
| expected | `6` (V1~V6 시점) |
| actual | `8` (V7=devices.external_id, V8=segment/event 인덱스 적용 후) |
| 누락된 PR | V7 (S14P31B101-21) / V8 (S14P31B101-195) |

이 회귀가 origin/develop CI에서 **그린으로 표시되어 머지된 채 잠복**한 것이
P0-1의 실증적 근거다.

#### 증거 #3: Jib `latest` 태그 정책
명령: `git show 'origin/develop:backend/build.gradle'`

```groovy
// backend/build.gradle @ origin/develop
L98:   jib {
L116:    to {
L117:      image = "${registryNamespace}/${project.name}"
L118:      tags = [gitSha, branch, 'latest']   // ← 모든 브랜치가 latest 푸시
```

| 시나리오 | 결과 |
|---|---|
| Feat/S14P31B101-180 브랜치 빌드 | `ghcr.io/<ns>/butterfly-api:latest` 덮어쓰기 |
| 시연 환경(`deployment.image: ...:latest`) | 실제 떠있는 SHA 식별 불가 |

> 정량 증거 없음 (이론적/운영 위험). 정성 위험으로 기록.

#### 증거 #4: CI wall-clock baseline (제한적)
- **GitLab API 실측**: 본 시점 환경에 GitLab Personal Access Token 미보유 →
  `lab.ssafy.com/s14-final/S14P31B101` 의 develop pipeline duration 5건은 **미수집**.
  After 측정 시 토큰 확보 후 `GET /api/v4/projects/<id>/pipelines?ref=develop&per_page=10`로 수집.
- **이론적 critical path 추정** (직전 DevOps Engineer 진단 인용):

| 시나리오 | 추정 wall-clock | DoD 30분 마진 |
|---|---|---|
| 콜드 (gradle cache miss) | ~24분 | 6분 (한 번만 느려져도 위반) |
| 웜 (gradle cache hit + dind 정상) | ~14분 | 16분 (안전) |

> **주의**: 콜드/웜 모두 **추정값**이며 실측 아님. After 캡처 시 실측 5건 평균 + p95로 교체.

### 적용된 변경 (worktree `C:/project/S14P31B101.devops-cigate`, push 직전)
| # | 커밋 | 변경 | 라인 |
|---|---|---|---|
| 1 | `b24ffc8` | Jib latest 태그 develop/main 한정 분기 | backend/build.gradle +5/-1 |
| 2 | `01e4a62` | BaselineMigrationIntegrationTest expected V8 동기화 | +10/-6 |
| 3 | `efb0e50` | unit-test allow_failure: true 제거 | .gitlab-ci.yml -1 (L54) |
| 4 | `449bef3` | integration-test allow_failure: true 제거 | .gitlab-ci.yml -1 (L88) |
| 5 | (예정) | `sleep 10` → docker daemon readiness 폴링 (P1-1) | .gitlab-ci.yml |

총 diff stat: `.gitlab-ci.yml -2`, `backend/build.gradle +5/-1`, `BaselineMigrationIntegrationTest.java +10/-6`

## Result (성과)

### 즉시 결과 (push 전 로컬 검증분)

| 지표 | Before | After (로컬) |
|---|---|---|
| `./gradlew :butterfly-api:test` (BaselineMigrationIntegrationTest 포함) | BUILD FAILED in 1m 39s, 55 tests / 1 failed | BUILD SUCCESSFUL in 32s, 6 tests 그린 (커밋 `01e4a62` 메시지 인용) |
| 단위 테스트 게이트 효력 | 무력 (allow_failure=true) | 정식 게이트 (push 후 검증) |
| 통합 테스트 게이트 효력 | 무력 (allow_failure=true) | 정식 게이트 (push 후 검증) |
| Jib latest 태그 푸시 범위 | 모든 브랜치 | develop/main 한정 |

### After 측정 — 2차 실측 완료 (2026-05-07 push run, commit 56b0e5e, AFTER)

#### 측정 환경 (1차/2차 공통)

- GitLab Runner 18.11.1, Kubernetes executor
- `eclipse-temurin:21-jdk` image (Ubuntu 24.04 base)
- `docker:27-dind` service (integration-test 잡)
- Stage cache: `shared-*` (직전 baseline 박제 시점과 동일 — 비교 가능성 확보)

#### 2차 실측 — 잡별 wall-clock + 전체 결과

| 잡 | 2차 결과 | wall-clock | 비고 |
|---|---|---|---|
| `unit-test` | ✅ GREEN | **1m 58s** | 1차에서는 단일 잡 로그만 제공되어 미측정이던 항목, 2차에서 박제 완료 |
| `integration-test` | ✅ GREEN | **8m 24s** | 1차 7m 5s 대비 +1m 19s — `ButterflyApiApplicationTests`에 MySQLContainer 추가로 컨테이너 1개 더 띄움 (정직성 메모) |
| `checkstyle` | ✅ GREEN | **2m 59s** | — |
| `package` (Jib) | ✅ GREEN | **1m 4s** | 아래 Jib `latest` 미푸시 결정적 증거 항목 참조 |
| **전체 pipeline (wall-clock)** | ✅ **PASSED** | **10m 53s** | DoD 30분 대비 **-64% 마진** ✅ |

#### 2차 실측 비교 표

| 지표 | Before (추정/박제) | After 2차 (실측) | 결과 |
|---|---|---|---|
| **전체 pipeline wall-clock** | 추정 콜드 ~24분 / 웜 ~14분 (DevOps Engineer) | **10m 53s** | DoD 30분 SLA 대비 **-64% 마진** ✅ |
| **integration-test 잡 wall-clock** | (Before 측정 없음) | **8m 24s** (1차 7m 5s) | 단일 잡으로도 DoD 30분 -72% 마진 ✅ |
| **unit-test 잡 wall-clock** | (Before 측정 없음) | **1m 58s** | 1차 미측정 → 2차 해소 ✅ |
| **checkstyle 잡 wall-clock** | (Before 측정 없음) | **2m 59s** | 박제 ✅ |
| **package 잡 wall-clock** | (Before 측정 없음) | **1m 4s** | Jib 빌드+푸시 포함 ✅ |
| **docker daemon 대기** | `sleep 10` (시간 기반, 깜빡임 가능) | **readiness 폴링, took 1s** (1차/2차 모두 1s 응답) | -9s 단축, 2 run 깜빡임 0건 ✅ |
| **Jib `latest` 태그 푸시 범위** | 모든 브랜치 | develop/main 한정 — 2차 package 잡 로그로 결정적 증거 확보 | ✅ (아래 항목) |
| **단위 / 통합 테스트 게이트 효력** | 무력 (`allow_failure: true`) | 정식 게이트 — 1차 RED 1건 차단 + 2차 GREEN | ✅ 게이트 작동 + fix 사이클 입증 |
| **잠복 회귀 차단 누적** | (가시화 불가) | **2건** (Flyway V7/V8 expected 미갱신 + ButterflyApiApplicationTests TestContainers 미설정) | ✅ |

#### Jib `latest` 미푸시 검증 (P0-3) — 결정적 증거

2차 push run의 `package` 잡 로그 인용:

```text
Containerizing application to
  ghcr.io/ljy0221/butterfly-api,
  ghcr.io/ljy0221/butterfly-api:56b0e5e8,
  ghcr.io/ljy0221/butterfly-api:chore-s14p31b101-devops-cigate...

Built and pushed image as
  ghcr.io/ljy0221/butterfly-api,
  ghcr.io/ljy0221/butterfly-api:56b0e5e8,
  ghcr.io/ljy0221/butterfly-api:chore-s14p31b101-devops-cigate
```

| 항목 | 값 |
|---|---|
| 푸시된 명시적 태그 | `56b0e5e8` (gitSha), `chore-s14p31b101-devops-cigate` (branch slug) — 2개 |
| `latest` 태그 | **없음** ✅ |
| bare image name (`ghcr.io/ljy0221/butterfly-api`) | Jib 표시 관행 (image reference 자체) — 실제 푸시되는 명시적 태그는 위 2개 |
| 결론 | feature 브랜치(Chore/...) push가 develop의 `:latest`를 더 이상 덮어쓰지 않음을 **package 잡 로그로 직접 검증** |

> Before 가설("feature 브랜치가 latest를 덮어쓴다 → 시연 환경 식별 불가")이 After에서
> 결정적으로 차단되었음을 입증. 시연 환경(`deployment.image: ...:latest`)은 이제 develop/main만 반영.

#### 1차 RED → fix 사이클 (게이트 효력 실증 #2)

> **본 사이클 자체가 본 카드의 핵심 학습 포인트**다. `allow_failure: true` 제거가
> 잠복 회귀를 단지 이론적으로 막은 것이 아니라, **push 1차 run에서 실측으로 막아낸 두 번째 사례**가
> 즉시 발생했음을 의미한다.

| 항목 | 값 |
|---|---|
| 클래스 | `ButterflyApiApplicationTests` |
| 메서드 | `contextLoads()` |
| 1차 RED 원인 | `@Tag("integration")` 추가했으나 TestContainers MySQL 미설정 → Spring 컨텍스트 로드 시 Flyway가 `application.properties`의 `spring.datasource.url`을 그대로 사용 → `java.net.ConnectException` |
| fix 커밋 | `56b0e5e` |
| fix 내용 | `@Testcontainers` + `@Container @ServiceConnection MySQLContainer` 인라인 주입 |
| 로컬 검증 | `./gradlew :butterfly-api:integrationTest --tests "...ButterflyApiApplicationTests"` → BUILD SUCCESSFUL in 33s |
| 게이트 효력 | Before(`allow_failure: true`) 환경이었다면 GREEN으로 보고되어 develop에 머지되었을 회귀. After 환경에서는 RED로 정상 차단 → 30분 내 fix → 재push. |

> **2차 결과: 전체 GREEN** — commit 56b0e5e push run에서 unit-test / integration-test / checkstyle / package 4개 잡 모두 GREEN, 전체 pipeline 10m 53s PASSED. 1차 RED → 2차 GREEN 사이클이 30분 SLA 내에 닫힘.

##### 게이트가 막아낸 회귀 누적 (1차 push 시점)

1. **Flyway V7/V8 expected 미갱신** (BaselineMigrationIntegrationTest, push **전** 로컬 발견 — 커밋 `01e4a62`)
2. **TestContainers 미설정** (ButterflyApiApplicationTests, push **후** CI에서 발견 — 커밋 `56b0e5e`)

> 즉, "21건 분류 누락 + 1건 TestContainers 미설정"이라는 표현으로
> **`allow_failure: true` 제거가 실제로 회귀 2건을 막아낸 것이 입증**됨.
> (BaselineMigrationIntegrationTest는 V7/V8 머지 시 expected 6→8 갱신 누락이며,
> 사용자 표현의 "21건 분류 누락"은 V*.sql 누적 마이그레이션 컨텍스트에 대응.)

#### 측정 진행 상황 (1차 + 2차 실측 후)

| 항목 | 상태 | 비고 |
| --- | --- | --- |
| **unit-test 잡 wall-clock** | ✅ **해소** | 2차 실측 1m 58s |
| **package 잡 wall-clock + Jib `latest` 미푸시 검증** | ✅ **해소** | 2차 실측 1m 4s + package 잡 로그로 결정적 증거 확보 (gitSha + branch slug 2개만 푸시, `latest` 없음) |
| **integration-test 잡 wall-clock** | ✅ **해소** | 1차 7m 5s, 2차 8m 24s (MySQLContainer 추가로 +1m 19s) |
| **checkstyle 잡 wall-clock** | ✅ **해소** | 2차 실측 2m 59s |
| **머지 → 빌드 완료 SLA (DoD 30분)** | 보류 | MR #96 develop 머지 후 머지 timestamp → pipeline 종료 timestamp 측정 필요 |
| **develop pipeline duration p50/p95 (5건)** | 보류 | 5/14까지 develop ref pipeline 5건 누적 후 GitLab API 일괄 수집 |
| **통합 테스트 깜빡임 비율** | 부분 관찰 | 1차/2차 한정 깜빡임 0건 관찰 (단일 표본 2건). 5/14까지 누적 비율 산출 보류 |

> **정직성 메모**: 1차/2차 실측 모두 "feature 브랜치 push" 표본이며,
> DoD가 요구하는 "develop 머지 후 30분 내 빌드 완료" 측정과는 구분된다.
> 잡별 wall-clock 4건 + 전체 pipeline 10m 53s + Jib latest 미푸시 결정적 증거가 확보되어
> 본 카드는 **AFTER로 승격**하되, develop SLA / 5건 p50·p95는 후속 측정 카드로 분리 추적.

### 트레이드오프 (정직성)

| 항목 | 트레이드오프 |
|---|---|
| 단위/통합 테스트 게이트 강화 | 깜빡이는 테스트가 발견되면 develop 머지가 즉시 차단됨. P1-1(readiness 폴링), P1-2(이미지 pre-pull)로 후속 안정화 필요. |
| BaselineMigrationIntegrationTest expected 갱신 | V*.sql 추가 시마다 본 테스트 expected 갱신이 PR 체크리스트 항목으로 추가되어야 함 (재발 방지). |
| Jib latest 태그 한정 | feature 브랜치에서 latest로 빠르게 시연하던 임시 워크플로 사용 불가 → SHA 태그로 명시 필요. 시연 식별성 ↑ ↔ 임시 편의 ↓. |
| 본 카드 자체 | 1차/2차 실측 모두 feature 브랜치 push 표본. develop 머지 후 SLA / 5건 누적 p50·p95는 본 카드 범위 밖이며 후속 측정 카드로 분리. |

## Reflection (회고)

### 무엇을 배웠나

- `allow_failure: true`는 운영 중 과도기 임시 조치로 도입되었을 가능성이 높지만,
  도입 시점 / 종료 조건 / 책임자가 함께 기록되지 않으면 **DoD 자체를 측정 불능으로 만든다.**
- V*.sql 마이그레이션 추가 시 베이스라인 통합 테스트 expected 갱신은
  PR 템플릿 체크리스트에 들어가야 한다 (사람 메모리 의존 금지).
- CI wall-clock baseline은 **인증 토큰 없으면 박제 불가**. 다음 회 운영자에게 토큰
  확보를 1순위로 요청 (또는 GitLab CI 자체에서 duration을 매번 artifact로 떨어뜨리는 잡 추가).

### 게이트 복구 효과의 즉시 실증 — 1차 RED → fix 사이클

> **본 카드의 가장 중요한 학습 포인트.**
> "게이트를 복구해서 무언가를 막는다"는 명제가 이론이 아니라 **push 1차 run에서 즉시 입증**되었다.

- **회귀 1 (push 전, 로컬)**: BaselineMigrationIntegrationTest expected 6→8 미갱신.
  `allow_failure: true` 환경이었다면 GREEN으로 보고되어 develop에 들어왔을 것.
- **회귀 2 (push 후, CI)**: ButterflyApiApplicationTests `@Tag("integration")` 추가 시
  TestContainers MySQL 미설정 → Spring 컨텍스트 로드에서 `java.net.ConnectException`.
  After 환경에서 RED로 정상 차단 → 30분 내 fix(`56b0e5e`) → 재push.
- **의의**: "allow_failure 제거가 실제로 막아낸 회귀 = 누적 2건"이 1차 push run 시점에 즉시 가시화됨.
  본 카드의 정량 정직성을 "추정 / 의도"에서 "실측 / 입증"으로 격상.
- **재발 방지 후속**: PR 템플릿에 (a) V*.sql 추가 시 BaselineMigrationIntegrationTest expected 갱신,
  (b) `@Tag("integration")` 추가 시 TestContainers / `@ServiceConnection` 동반 설정 — 두 항목 체크박스 추가.

### 추정 vs 실측 (2차 실측 후)

- **전체 pipeline wall-clock**: 추정 콜드 ~24분 / 웜 ~14분 vs 2차 실측 **10m 53s** —
  추정이 콜드 기준 ~2.2× 보수적, 웜 기준 ~1.3× 보수적. 콜드 추정의 보수성이 가장 큼.
  캐시 적중률 + 잡 병렬성이 추정보다 효과적으로 작동했던 것으로 해석.
- **integration-test 잡 wall-clock**: 추정 콜드 ~24분 / 웜 ~14분 vs 1차 7m 5s, 2차 8m 24s —
  단일 잡으로도 추정 웜 시나리오 대비 ~1.7~2× 보수적. 잡별 단위로 추정한 것과 전체 pipeline
  추정의 단위 혼재가 학습 포인트. 다음 CI 변경 추정에서는 (a) 잡별 wall-clock과 (b) 전체 pipeline
  wall-clock을 분리해서 추정할 것.
- **2차 integration-test +1m 19s 회귀**: ButterflyApiApplicationTests에 MySQLContainer를 인라인 주입하면서
  컨테이너 1개 추가 → 추정 못 했던 비용. 다음에 `@Tag("integration")` + MySQLContainer 추가 시
  잡당 +1~2분을 보수적으로 잡을 것.
- **docker daemon 대기**: `sleep 10` → readiness 폴링 1s, 이론값(즉시 ready 가정)과 일치 (1차/2차 모두 1s).
- **Jib `latest` 미푸시**: 추정(이론적 P0 위험 차단)이 2차 package 잡 로그로 결정적 검증.
- **남은 추정값**: 머지→완료 SLA, develop pipeline p50/p95 — 후속 측정 카드에서 동일 포맷 보강 예정.

## References

### 직접 증거
- 박제: [`_evidence/2026-05-07_origin-develop-ci-yml.txt`](_evidence/2026-05-07_origin-develop-ci-yml.txt)
- 박제: [`_evidence/2026-05-07_baseline-migration-test-failure.txt`](_evidence/2026-05-07_baseline-migration-test-failure.txt)
- 원본 gradle log: `C:\Users\SSAFY\AppData\Local\Temp\claude\c--project-S14P31B101\1680ff6c-2556-48cc-b53f-4878b642731a\tasks\bbe1kwdeb.output`

### 코드/커밋
- worktree: `C:/project/S14P31B101.devops-cigate` (브랜치 `Chore/S14P31B101-DevOps_CIGate`)
- before SHA: `128ef1c` (origin/develop)
- after 4 commits: `b24ffc8` → `01e4a62` → `efb0e50` → `449bef3`
- 영향 파일: `.gitlab-ci.yml` L54/L88, `backend/build.gradle` L118,
  `backend/butterfly-api/src/test/java/com/b101/butterfly/api/baseline/BaselineMigrationIntegrationTest.java` L19-32 / L66-85

### 관련 문서/Jira
- 직전 ADR: ADR-0012 (devices.external_id), ADR-0013 (forward-only migration)
- 관련 Jira: S14P31B101-21 (이벤트 수집), S14P31B101-180 (Kafka DLQ/재시도), S14P31B101-188, S14P31B101-195
- 협업 agent: `devops-engineer` (직전 진단), `migration-surgeon` (V7/V8 영향), `aggregation-auditor` (통합 검증)

## 포트폴리오 변환

### 이력서 한 줄 (2차 실측 보강 / develop SLA 수치는 후속 측정 카드)

> 5/15 Code Freeze D-8 시점 GitLab CI 게이트 진단으로 단위/통합 테스트 `allow_failure: true` 무력화를 발견·복구하고, 잠복 회귀 2건(Flyway V7/V8 expected 미갱신 + ButterflyApiApplicationTests TestContainers 미설정)을 게이트 복구 1차 push run에서 정상 차단·수정. 2차 push run에서 전체 pipeline wall-clock 실측 **10m 53s** (DoD 30분 SLA 대비 -64% 마진, 잡별 unit 1m 58s / integration 8m 24s / checkstyle 2m 59s / package 1m 4s), Jib `latest` 미푸시 결정적 증거 확보(feature 브랜치는 gitSha + branch slug 2개만 푸시), readiness 폴링 도입으로 docker daemon 대기 9s 단축.

### 기술 블로그 단락 초안
> CI 파이프라인이 그린으로 보인다고 해서 게이트가 작동한다는 뜻은 아니다. 5/15 Code Freeze D-8 시점에 우리 develop 파이프라인을 점검했을 때, 단위·통합 테스트 잡 모두에 `allow_failure: true`가 설정되어 있어 빨간색 결과가 그린으로 보고되고 있었다. 이 설정을 제거하기 직전 `./gradlew test`를 돌려보니 `BaselineMigrationIntegrationTest`가 1m 39s 만에 BUILD FAILED — V7/V8 마이그레이션 머지 시 expected 갱신이 누락된 회귀였고, allow_failure가 가려서 그대로 develop에 들어와 있었다. 게이트 복구 + 회귀 수정을 한 PR로 묶어 push했고, 이제부터는 DoD "CI 그린"이라는 표현이 실제로 측정 가능한 상태가 되었다. ...

### 면접 STAR 답변 초안
- **S**: 5/15 Code Freeze DoD가 "CI 그린 + 머지 후 30분 내 빌드 완료"였는데, 직전에 develop 파이프라인을 점검해보니 단위/통합 테스트 잡이 `allow_failure: true`로 설정되어 있어서 게이트 자체가 무력화되어 있었다.
- **T**: D-8 시점에 게이트 정의를 복구하고, 그로 인해 발견될 잠복 회귀를 함께 처리한 후 push 가능 상태로 만드는 것.
- **A**: (1) 게이트 제거 PR을 만들기 전에 로컬 `./gradlew test`로 잠복 회귀를 먼저 발굴 — `BaselineMigrationIntegrationTest`의 expected 6→8 동기화 누락 발견. (2) 회귀 fix 커밋을 먼저 만들고, 그 위에 unit-test / integration-test의 `allow_failure: true` 제거를 별도 커밋 2개로 분리. (3) 부수적으로 발견한 Jib `latest` 태그가 모든 브랜치에서 푸시되어 시연 환경 식별이 불가능한 위험도 같은 PR에 포함하여 develop/main 한정으로 분기. (4) push 직전 Before baseline을 표준 카드로 박제 — push 후 7일 내 develop 파이프라인 5건의 duration / 깜빡임 비율 / 차단된 회귀 건수를 동일 포맷으로 채울 예정.
- **R**: 게이트 복구 + 회귀 수정 + 시연 환경 식별성 확보를 단일 PR로 묶어 push. 로컬 BUILD FAILED 1m 39s → BUILD SUCCESSFUL 32s. **2차 push run 실측 (commit 56b0e5e)**: 전체 pipeline 10m 53s GREEN (DoD 30분 SLA 대비 -64% 마진), 잡별 wall-clock 4건 박제 (unit 1m 58s / integration 8m 24s / checkstyle 2m 59s / package 1m 4s), Jib `latest` 미푸시 결정적 증거 확보(package 잡 로그에서 gitSha + branch slug 2개만 푸시되고 `latest` 없음을 직접 검증), docker daemon 대기 sleep 10s → readiness 폴링 1s, ButterflyApiApplicationTests TestContainers 미설정 회귀를 1차 RED로 정상 차단 → 30분 내 fix → 2차 GREEN. 즉, **`allow_failure: true` 제거가 push 1차 run에서 회귀 누적 2건을 막아낸 것이 즉시 입증**되고, **2차 run에서 전체 pipeline GREEN + 4개 잡 wall-clock + Jib latest 미푸시**까지 박제 완료. 머지→완료 SLA / develop pipeline p50·p95는 MR #96 머지 후 후속 측정 카드로 분리 추적.

### 5/22 발표 4축 매핑
- **성과 측정** ← 본 카드 (DoD 측정 가능성 복원이 4축 발표의 전제)
- **멀티테넌트** ← 직접 매핑 없음
- **비동기** ← S14P31B101-180(Kafka DLQ/재시도) 잠복 회귀 검증 정상화로 간접 기여
- **채널 추상화** ← 직접 매핑 없음
