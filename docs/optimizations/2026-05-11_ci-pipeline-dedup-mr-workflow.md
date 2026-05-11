---
date: 2026-05-11
slug: ci-pipeline-dedup-mr-workflow
category: DX
tags: [#ci, #gitlab-ci, #workflow-rules, #mr-pipeline, #ghcr, #devops, #cost]
related-jira: S14P31B101-220
related-epic: S14P31B101-44
related-branch: Refactor/S14P31B101-220_CIWorkflowDedup
git-before: e337c43 (develop, "Merge branch 'feat/S14P31B101-105_ValidateSegmentOnTrigger'")
git-after: 8b1d710 ([S14P31B101-220] ci(gitlab): workflow 블록 + MR pipeline 전환으로 중복 트리거 제거)
diff-stat: ".gitlab-ci.yml +33/-3 (1 file changed)"
agent-source: optimization-historian
status: BEFORE-ONLY  # 코드 변경 + 정적 분석은 완료. wall-clock 절감 실측은 머지 후 5/14까지 누적 측정으로 분리 추적.
predecessor-card: 2026-05-07_ci-gate-restoration.md  # CI 게이트 정의 복원 → 본 카드는 그 위에서 중복 트리거 정리
successor-measurement: MR #(TBD) 머지 후 develop pipeline 5건 누적, dry-run MR로 파이프라인 1개 표시 검증
---

# GitLab CI 중복 트리거 제거 — workflow 블록 + MR pipeline 전환

## 한 줄 요약

**`.gitlab-ci.yml`에 GitLab 표준 `workflow:` 블록을 도입해 branch pipeline ↔ MR pipeline 중복 실행을 차단하고, `package` 잡은 MR pipeline에서 명시적으로 스킵하여 GHCR에 검증되지 않은 이미지 태그가 누적되는 위험을 동시에 차단. 코드 라인 `+33/-3`으로 (a) feature 머지 시 동일 SHA 재실행 1회 절감 + (b) MR pipeline 도입의 안전한 활성화 토대 마련 + (c) GHCR 태그 오염 차단의 세 효과를 한 번에 확보.**

> 본 카드는 변경 commit (`8b1d710`) 직후 박제되었다. **wall-clock 절감 실측은 본 카드 범위 밖**이며, 머지 후 develop / MR pipeline 누적 5건을 통해 [후속 측정 카드](#후속-측정-checklist) 로 분리 추적한다.

## Situation (배경)

### 직전 develop의 CI 트리거 모델 (Before)

직전 develop (`e337c43`) 시점 `.gitlab-ci.yml`에는 두 가지 부재가 있었다.

1. **`workflow:` 블록 부재** — pipeline 자체의 실행 가부를 결정하는 최상위 게이트가 없음. GitLab 기본 동작(모든 source에 대해 잡 rules 평가)이 그대로 적용됨.
2. **잡 rules에 `if` 분기 부재** — `unit-test`, `integration-test`, `checkstyle`, `frontend-build` 모두 `rules: - changes: ...` 만 사용. `CI_PIPELINE_SOURCE` / `CI_COMMIT_BRANCH` 분기 없음.

이 조합은 다음 3개의 회귀 위험으로 이어진다.

| # | 시나리오 | 문제 |
|---|---|---|
| 1 | feature 브랜치 마지막 push (이미 검증됨) → develop 머지 | 같은 코드, 다른 SHA로 develop branch pipeline이 다시 한 번 통째로 실행 (test 3개 + frontend 1개 + package matrix 2개) |
| 2 | GitLab 프로젝트 설정에서 MR pipeline 활성화 | branch pipeline + MR pipeline이 동일 SHA에 동시 실행 (잡 6개+ × 2 동시) |
| 3 | MR pipeline에서 `package` 잡 매칭 | Jib가 `:CI_COMMIT_SHORT_SHA` 태그로 GHCR에 push → 검증되지 않은 MR commit 이미지 태그가 GHCR에 누적 |

### 5/15 Code Freeze DoD 와의 관계

- 직전 카드 [`2026-05-07_ci-gate-restoration`](2026-05-07_ci-gate-restoration.md) 에서 `allow_failure: true` 제거로 게이트 **정의** 를 복구. 본 카드는 그 위에서 게이트 **실행 비용 / 실행 횟수** 를 정리.
- DoD "머지 후 30분 내 빌드 완료" 마진 확보 — 동일 SHA 중복 실행 1회 절감은 마진을 그대로 늘린다.
- 5/22 발표 4축 중 **성과 측정** 축의 운영 비용 항목.

### 관련 컨텍스트

- 직전 카드: [`2026-05-07_ci-gate-restoration`](2026-05-07_ci-gate-restoration.md) — 본 카드는 그 후속 정합성 작업
- 변경된 파일: `.gitlab-ci.yml` 단일
- 사용자 역할: 분석/대시보드/통합 검증(역할 6) — CI 신뢰성/비용은 4축 "성과 측정" 직접 영향
- Jira Epic: S14P31B101-44 (품질/관측성/배포)

## Task (목표)

### 단기 (본 PR 머지까지)

- branch pipeline ↔ MR pipeline 중복 실행 차단 (회귀 위험 #1, #2)
- `package` 잡이 MR pipeline에서 실행되지 않도록 명시적으로 차단 (회귀 위험 #3)
- 변경 범위는 `.gitlab-ci.yml` 단일 파일 — 스키마/도메인 코드 무영향

### 중기 (MR 머지 후 5/14까지)

- dry-run MR을 만들어 GitLab UI에서 동일 SHA에 pipeline이 1개만 표시되는지 결정적 증거 확보
- develop 머지 후 develop branch pipeline이 정상적으로 1회 실행되며 `package` 잡이 GHCR push까지 완주하는지 확인
- develop pipeline duration 5건 누적 → 직전 카드의 후속 측정과 동일 포맷으로 보강

## Action (조치)

### Before 상태 — 증거

#### 증거 #1: workflow 블록 부재 + 잡 rules의 if 분기 부재

원본 박제: [`_evidence/2026-05-11_gitlab-ci-yml-before-e337c43.txt`](_evidence/2026-05-11_gitlab-ci-yml-before-e337c43.txt)
명령: `git show e337c43:.gitlab-ci.yml`

```yaml
# .gitlab-ci.yml @ e337c43 (Before)
L7:  default:
L8:    interruptible: true
L9:
L10: stages:                    # ← workflow 블록 없이 바로 stages
L11:   - test
L12:   - package
...
L51: unit-test:
L52:   stage: test
L53:   image: eclipse-temurin:21-jdk
L54:   rules:
L55:     - changes:             # ← if 분기 없음 — branch / MR 모두 동일 매칭
L56:         - backend/**/*
L57:         - .gitlab-ci.yml
...
L91: integration-test:           # 동일 패턴
L94:   rules:
L95:     - changes: [...]
...
L123: checkstyle:                # 동일 패턴
L126:   rules:
L127:     - changes: [...]
...
L145: package:
L148:   rules:
L149:     - if: $CI_COMMIT_REF_NAME == "main"          # ← MR 차단 없음
L150:     - if: $CI_COMMIT_REF_NAME == "develop"
L151:     - if: $CI_COMMIT_REF_NAME =~ /^(Feat|Fix|Hotfix|Chore)\//
L152-153:        changes: [...]
L154:     - if: $CI_COMMIT_REF_NAME =~ /^BE\/(Feat|Fix|Hotfix|Chore)\//
L155-156:        changes: [...]
...
L189: frontend-build:            # 동일 패턴
L192:   rules:
L193:     - changes: [...]
```

> **사용자에게 보일 한 줄**:
> "feature 브랜치가 검증된 SHA로 develop에 머지되면, GitLab은 같은 코드를 머지 commit SHA로 다시 한 번 통째로 검증한다. MR pipeline을 켜는 순간 같은 SHA가 동시에 두 번 실행될 위험도 있다."

#### 증거 #2: 회귀 위험 #1 의 중복 실행 비용 (정성/구조 추정)

직전 카드 [`2026-05-07_ci-gate-restoration`](2026-05-07_ci-gate-restoration.md) 의 **2차 실측**(commit `56b0e5e`) 잡별 wall-clock 인용:

| 잡 | 2차 실측 wall-clock |
|---|---|
| unit-test | 1m 58s |
| integration-test | 8m 24s |
| checkstyle | 2m 59s |
| package (Jib, parallel matrix 2) | 1m 4s |
| **전체 pipeline** | **10m 53s** |

- feature → develop 머지 시점에 같은 코드를 한 번 더 검증하면 **runner 자원 ~10m+ × 1회 / 머지** 가 절감 가능 (정확 절감량은 본 카드 범위 밖).
- `.gitlab-ci.yml` 주석에 명시된 `GRADLE_OPTS workers.max=2` / `integration-test --no-parallel` 은 **OOM 회피용 의도적 직렬화** 라서 추가 절감 여지가 작음 → 중복 실행 1회 절감의 한계 효용이 더 큼.

> **정직성 메모**: 위 표는 직전 카드의 feature 브랜치 push 표본이며, develop 머지 commit의 실측은 아니다. 본 카드의 절감 효과 정량은 후속 측정에서 동일 포맷으로 보강한다.

#### 증거 #3: 회귀 위험 #3 의 GHCR 태그 오염 가능성 (정성)

`backend/build.gradle` 의 Jib 설정은 직전 카드에서 `develop/main` 한정으로 정리되어 `latest` 태그 위험은 차단됨. 다만:

- 명시적 분기를 거친 결과 `gitSha`(commit short SHA) + `branch slug` 두 태그는 모든 매칭 브랜치에서 push.
- MR pipeline이 활성화된 상태에서 `package` 잡 차단이 없으면 **MR commit의 SHA 태그가 GHCR에 매번 push** 되어 검증되지 않은 이미지가 누적.
- 문제 본질: "검증된 head 가 아닌 commit 의 이미지가 registry 에 들어간다."

### 적용된 변경 (commit `8b1d710`)

원본 박제: [`_evidence/2026-05-11_gitlab-ci-yml-diff-8b1d710.txt`](_evidence/2026-05-11_gitlab-ci-yml-diff-8b1d710.txt)
명령: `git show 8b1d710 -- .gitlab-ci.yml`

#### 변경 1 — 최상위 `workflow:` 블록 추가 (After 16~21행)

```yaml
# branch pipeline ↔ MR pipeline 중복 방지.
# MR이 열린 브랜치에 push하면 branch pipeline을 차단하고 MR pipeline만 돌린다.
# - feature push (MR 없음): branch pipeline 1회
# - MR 생성 이후 push: MR pipeline 1회 (branch pipeline 자동 차단)
# - develop/main merge commit: branch pipeline 1회 (통합 검증 + package)
workflow:
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
      when: never
    - if: $CI_COMMIT_BRANCH
```

GitLab 공식 문서 권장 패턴(소위 "MR pipeline switchover") 그대로 채택. 룰의 의미:

| rule | 의미 | 효과 |
|---|---|---|
| 1 | MR pipeline은 무조건 허용 | MR이 열린 후의 push는 MR pipeline으로만 실행 |
| 2 | branch에 MR이 열려 있으면 branch pipeline은 never | 동일 SHA 두 번 실행 차단 |
| 3 | 그 외 branch push 허용 | MR 없는 feature push, develop/main 머지 commit |

#### 변경 2 — `unit-test` / `checkstyle` / `frontend-build` 잡 rules에 `if` 분기 명시

각 잡의 `rules: - changes: ...` 단일 항목을 두 개의 if 분기로 분리:

```yaml
rules:
  - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    changes:
      - backend/**/*           # frontend-build는 frontend/**/*
      - .gitlab-ci.yml
  - if: $CI_COMMIT_BRANCH
    changes:
      - backend/**/*
      - .gitlab-ci.yml
```

- workflow 블록과 잡 rules는 AND 관계. 잡 rules가 source를 명시하지 않으면 workflow 통과 후 무조건 평가되어 의도치 않게 켜질 수 있음 — 이중 안전장치.
- `integration-test` 잡은 본 PR 범위에서는 변경하지 않음(직전 카드의 readiness 폴링 / `--no-parallel` 직렬화 의도 보존). 후속 PR에서 동일 패턴으로 확장 검토.

#### 변경 3 — `package` 잡 rules 최상단 MR 차단

```yaml
package:
  ...
  rules:
    # MR pipeline에서는 이미지 빌드 스킵 (test 잡으로 검증 완료, GHCR 태그 오염 방지)
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
      when: never
    - if: $CI_COMMIT_REF_NAME == "main"
    - if: $CI_COMMIT_REF_NAME == "develop"
    - if: $CI_COMMIT_REF_NAME =~ /^(Feat|Fix|Hotfix|Chore)\//
      changes: [...]
    - if: $CI_COMMIT_REF_NAME =~ /^BE\/(Feat|Fix|Hotfix|Chore)\//
      changes: [...]
```

- `when: never` 가 최상단에 위치 → MR pipeline 매칭 시 이후 룰을 평가하지 않고 즉시 스킵.
- branch pipeline에서는 기존 분기(main/develop/Feat/.../BE/...) 그대로 적용 → develop 머지 commit에서 GHCR push 정상 동작.

### diff stat

```
.gitlab-ci.yml | 36 +++++++++++++++++++++++++++++++++---
1 file changed, 33 insertions(+), 3 deletions(-)
```

## Result (성과)

### 정량 — 코드 / 구조 변경 (즉시 측정 가능)

| 지표 | Before | After (commit 8b1d710) |
|---|---|---|
| `.gitlab-ci.yml` 최상위 workflow 블록 | 없음 | 6행 (rules 3개) |
| 잡 rules의 source 분기 명시 (`unit-test` / `checkstyle` / `frontend-build`) | 0개 | 6개 (각 잡 2개 분기) |
| `package` 잡의 MR 차단 룰 | 없음 | 1개 (`when: never` 최상단) |
| `.gitlab-ci.yml` diff | — | +33 / -3 (1 file) |
| 잠재 중복 실행 시나리오 | 3개 (위 표 참조) | 0개 (룰 정의 기준) |
| GHCR에 push될 잠재 MR commit 이미지 태그 | 매 MR push마다 1개 | 0개 (정의 기준) |

### 정량 — wall-clock 절감 (후속 측정 대상, 본 카드 범위 밖)

| 지표 | Before (추정) | After (목표) | 측정 시점 |
|---|---|---|---|
| feature → develop 머지 시 동일 SHA 중복 실행 | 1회 (~10m+) | 0회 | 본 PR 머지 후 develop pipeline 1건 |
| MR pipeline 활성화 후 동일 SHA 동시 실행 | branch + MR 2개 | MR 1개 | dry-run MR 1건 |
| MR pipeline 1회당 잡 수 | 6개+ (test 3 + frontend 1 + package matrix 2) | 4개 (test 3 + frontend 1, package 차단) | dry-run MR 1건 |
| GHCR에 push되는 commit 이미지 태그 (MR push당) | 1개+ | 0개 | dry-run MR 1건 |

> **정직성 메모**: 위 절감 폭은 **룰 정의에 근거한 구조적 기댓값**이며 wall-clock 실측이 아니다. 5/14까지 develop 5건 / MR dry-run 1건 누적 후 [후속 측정](#후속-측정-checklist) 에서 동일 포맷으로 보강.

### 정성

- 5/15 Code Freeze DoD ("머지 후 30분 내 빌드 완료") 마진 확장 — 동일 SHA 중복 실행이 사라지는 만큼 runner 슬롯이 다음 머지에 즉시 양보됨.
- GitLab 프로젝트 설정에서 MR pipeline을 안전하게 활성화할 수 있는 토대 — 본 카드 적용 전에는 활성화 즉시 회귀 위험 #2/#3가 동시 발생.
- `.gitlab-ci.yml` 주석에 의도가 명문화되어 후속 운영자가 MR pipeline의 의미를 즉시 파악 가능.

### 트레이드오프 (정직성)

| 항목 | 트레이드오프 |
|---|---|
| `package` 잡을 MR pipeline에서 차단 | MR 단계에서는 Jib 빌드 자체의 실패(예: dockerfile 오류, base image pull 실패)가 가시화되지 않음. branch pipeline(머지 후 develop)에서 처음 발견될 수 있음 → 머지 직후 develop 모니터링 책임이 더 무거워짐. |
| MR pipeline 전환의 룰 복잡성 증가 | `unit-test`/`checkstyle`/`frontend-build` 각 잡 rules가 단일 changes에서 2개 if 분기로 늘어남 (가독성 -). 주석으로 보완. |
| `integration-test` 잡은 본 PR 범위에서 변경 없음 | 다른 잡과 패턴 비대칭. 후속 PR에서 동일 패턴 확장 필요 (직전 카드의 readiness 폴링 / `--no-parallel` 직렬화 의도와 충돌 없음을 별도 검증 필요). |
| 본 카드 자체 | wall-clock 절감 실측은 본 카드 범위 밖. 후속 측정 카드로 분리 추적. |

## Reflection (회고)

### 왜 GitLab 표준 workflow 패턴을 채택했는가

후보 3안:

| 안 | 내용 | 채택 여부 |
|---|---|---|
| A. 잡 rules에만 `merge_request_event` 분기 추가 | workflow 블록 없이 각 잡에서만 차단 | ❌ workflow 평가 → 잡 평가 순서상 잡 rules는 AND 조건이라 source 매칭이 빠진 잡이 새로 추가될 때마다 회귀 위험 재발. 잡 추가 비용↑ |
| B. workflow 블록 + 잡 rules는 그대로 | 최상위만 차단, 잡 rules는 changes만 | ❌ workflow 통과 후 잡 rules가 항상 매칭되어 의도치 않게 잡이 켜질 수 있음. 직접 잡 추가 시 안전성↓ |
| **C. workflow 블록 + 잡 rules에 if 분기 명시 (이중 안전장치)** | 본 PR 채택 | ✅ GitLab 공식 문서 권장 패턴. 회귀 위험 #1, #2를 모두 차단 + 신규 잡 추가 시에도 패턴 복사로 안전 보장. |

### 왜 `package` 잡을 MR에서 차단하는가 — 트레이드오프 결정

- **차단 안 했을 때**: MR push마다 GHCR에 검증되지 않은 commit SHA 이미지가 누적. 시연 환경의 이미지 식별성↓, registry storage 비용↑, GHCR retention 정책 추가 필요.
- **차단했을 때**: Jib 빌드 자체의 회귀(드물지만 base image pull 실패 등)가 MR에서는 안 잡힘. 머지 직후 develop branch pipeline에서 발견 → fix → 재머지의 사이클이 짧으므로 수용 가능 비용으로 판단.
- **결정**: 차단을 채택. 단, 후속 측정 단계에서 develop 머지 후 package 잡 회귀 비율을 모니터링 항목으로 박제.

### `integration-test` 를 본 PR 범위에서 제외한 이유

- 직전 카드 [`2026-05-07_ci-gate-restoration`](2026-05-07_ci-gate-restoration.md) 의 readiness 폴링 / `--no-parallel` 직렬화 의도가 잡 본문에 박혀 있음. workflow 블록 도입과 동시에 잡 rules도 건드리면 변경 영향이 섞여 후속 디버깅 비용이 커짐.
- 본 PR은 "workflow 블록 도입 + 가벼운 잡 3건의 if 분기 명시 + package 차단" 까지로 한정 — 책임 한 PR 한 가지 원칙.
- 후속 PR에서 `integration-test` 도 동일 패턴 확장 검토 (회귀 0건 확인 후 일괄 적용).

### 추정 vs 실측 (현 시점, 본 카드 범위)

- **잠재 중복 실행 시나리오 0개로 감소**: 룰 정의에 근거한 정적 평가 — 결정적.
- **wall-clock 절감 1회 ~10m+**: 직전 카드 2차 실측(`56b0e5e`, 10m 53s) 인용한 추정. 실측은 본 PR 머지 후 develop pipeline 1건 + dry-run MR 1건으로 보강.
- **GHCR 태그 누적 0**: 룰 정의에 근거한 정적 평가 — dry-run MR로 결정적 증거 확보 예정.

## 후속 측정 checklist

본 카드는 BEFORE-ONLY로 박제된다. 다음 항목들이 채워진 시점에 카드 상태를 AFTER (또는 후속 측정 카드 분리)로 갱신.

- [ ] **dry-run MR**: 본 PR 머지 직전 임시 MR을 1건 생성하여 GitLab UI에서 동일 SHA에 pipeline이 **1개만** 표시되는지 스크린샷 박제 → `_evidence/2026-05-1?_dryrun-mr-pipeline-count.png`
- [ ] **MR pipeline 잡 매트릭스**: dry-run MR pipeline에서 실행된 잡 목록을 캡처 — `unit-test` / `integration-test` / `checkstyle` / `frontend-build` 만 매칭되고 `package` 가 매칭되지 않음을 결정적 증거로 확보
- [ ] **develop branch pipeline 정상 실행**: 본 PR 머지 후 develop pipeline 1건이 통째로 실행되며 `package` 잡이 GHCR push까지 완주하는지 확인 (commit SHA + branch slug 2개 태그가 push되고 `latest` 는 develop 머지에서만 push되는지)
- [ ] **develop pipeline duration 5건 누적 (5/14까지)**: GitLab API `GET /api/v4/projects/<id>/pipelines?ref=develop&per_page=10` 로 head 5건의 duration p50/p95 측정 → 직전 카드와 동일 포맷으로 보강
- [ ] **MR pipeline duration 5건 누적 (5/14까지)**: `GET /api/v4/projects/<id>/pipelines?source=merge_request_event&per_page=10` 로 head 5건 duration 측정 — branch pipeline 대비 잡 수 감소(`package` 차단)에 따른 단축 확인
- [ ] **GHCR 태그 카운트 비교 (5/14까지)**: ghcr.io의 `butterfly-api` / `butterfly-worker` 태그 누적 수가 본 PR 전후로 어떻게 달라지는지 (매 MR마다 +1씩 늘던 추세가 멎는지)
- [ ] **회귀 0건 확인**: `package` 잡 MR 차단으로 인해 develop 머지 직후 Jib 빌드 회귀가 발생하는지 5/14까지 누적 모니터링

## References

### 직접 증거

- 박제: [`_evidence/2026-05-11_gitlab-ci-yml-before-e337c43.txt`](_evidence/2026-05-11_gitlab-ci-yml-before-e337c43.txt) — Before 전체 yml
- 박제: [`_evidence/2026-05-11_gitlab-ci-yml-diff-8b1d710.txt`](_evidence/2026-05-11_gitlab-ci-yml-diff-8b1d710.txt) — 변경 diff 본문 + 관전 포인트

### 코드/커밋

- branch: `Refactor/S14P31B101-220_CIWorkflowDedup`
- before SHA: `e337c43` (develop merge "feat/S14P31B101-105_ValidateSegmentOnTrigger")
- after SHA: `8b1d710` ([S14P31B101-220] ci(gitlab): workflow 블록 + MR pipeline 전환으로 중복 트리거 제거)
- 영향 파일: `.gitlab-ci.yml` (1 file, +33/-3)
  - workflow 블록 신설: 16~21행 (After)
  - unit-test rules: 65~74행 (After)
  - checkstyle rules: 142~151행 (After)
  - package rules: 169~172행 (After, 최상단 MR 차단 추가)
  - frontend-build rules: 216~225행 (After)

### 관련 문서/Jira

- 직전 카드: [`2026-05-07_ci-gate-restoration`](2026-05-07_ci-gate-restoration.md) — `allow_failure: true` 제거 + Flyway V7/V8 fix + Jib latest 한정 + readiness 폴링 (본 카드의 직접적 토대)
- Jira: S14P31B101-220 (본 티켓), S14P31B101-44 (Epic: 품질/관측성/배포)
- 협업 agent: `devops-engineer` (CI 진단 후보), `optimization-historian` (본 카드 작성)

## 포트폴리오 변환

### 이력서 한 줄 (현 시점, 코드 변경 + 정적 평가)

> 5/15 Code Freeze D-4 시점 GitLab CI 중복 트리거 진단으로 `.gitlab-ci.yml` 최상위 `workflow:` 블록 + 잡 rules의 `merge_request_event` 분기를 도입해 branch pipeline ↔ MR pipeline 동일 SHA 중복 실행 시나리오 3개를 룰 정의 기준 0개로 차단. `package` 잡은 MR pipeline에서 명시적으로 스킵하여 GHCR에 검증되지 않은 commit SHA 이미지가 누적되는 위험까지 한 PR(`+33/-3`)으로 동시에 차단.

### 이력서 한 줄 (후속 측정 보강 후, 골격)

> ... 위 구조 변경의 결과로 develop 머지 시 동일 SHA 중복 실행 1회(약 10m+) 절감, MR pipeline duration p50 N분 / p95 N분, GHCR `butterfly-api` 태그 누적 추세 정지를 5/14까지 누적 5건으로 박제.

### 기술 블로그 단락 초안

> CI 파이프라인의 중복 트리거는 보통 두 가지 경로로 들어온다. 첫째, feature 브랜치의 마지막 push와 develop 머지 commit이 같은 코드를 다른 SHA로 두 번 검증한다. 둘째, GitLab 프로젝트 설정에서 MR pipeline을 켜는 순간 같은 SHA가 branch pipeline과 MR pipeline에서 동시에 실행된다. 우리는 직전 PR에서 `allow_failure: true`를 제거하면서 게이트 정의 자체를 복원했고, 이번 PR에서는 그 위에서 게이트가 실행되는 횟수를 정리했다. GitLab 공식 문서 권장 패턴인 `workflow:` 블록 — `merge_request_event` 우선, 열린 MR이 있는 branch는 차단, 그 외 branch 허용 — 을 그대로 채택했다. 추가로 `package` 잡(Jib → GHCR push)은 MR pipeline에서 명시적으로 스킵해서 검증되지 않은 commit SHA 이미지가 GHCR에 누적되는 사이드 이펙트도 같이 막았다. 변경은 `.gitlab-ci.yml` 한 파일 `+33/-3`으로 끝났고, 정량 절감 측정은 머지 후 develop 5건 / dry-run MR 1건으로 분리해 추적한다. ...

### 면접 STAR 답변 초안

- **S**: 5/15 Code Freeze D-4 시점에 직전 PR로 단위/통합 테스트의 `allow_failure: true` 를 제거해 게이트 정의는 복원했지만, 그 위에서 같은 SHA가 두 번 검증되거나 MR pipeline 활성화 시 동시에 실행될 위험이 그대로 남아 있었다. GHCR 태그 오염도 잠재 위험으로 함께 잡혀 있었다.
- **T**: `.gitlab-ci.yml` 한 파일 변경으로 (a) feature 머지 시 동일 SHA 중복 실행, (b) MR pipeline 활성화 시 동시 실행, (c) MR pipeline에서 검증되지 않은 commit이 GHCR에 push되는 세 회귀 위험을 한 번에 차단.
- **A**: (1) 후보 3안 비교 — 잡 rules만, workflow만, 둘 다(이중 안전장치) — 중에서 GitLab 공식 권장인 "workflow + 잡 rules의 source 분기 명시" 를 채택. (2) `workflow:` 블록 6행 신설 (`merge_request_event` 우선, `CI_OPEN_MERGE_REQUESTS` 있는 branch는 never, 그 외 branch 허용). (3) `unit-test`/`checkstyle`/`frontend-build` 잡 rules에 `if` 분기 명시(잡 rules가 source를 명시하지 않으면 workflow 통과 후 의도치 않게 켜질 수 있음을 차단). (4) `package` 잡 rules 최상단에 MR 차단(`when: never`) 추가 — Jib가 검증되지 않은 commit SHA 이미지를 GHCR에 push하지 않도록. (5) `integration-test` 는 직전 PR의 readiness 폴링 / `--no-parallel` 의도 보존을 위해 본 PR 범위에서 제외(책임 한 PR 한 가지 원칙). (6) 변경 직후 표준 카드로 박제, wall-clock 절감 실측은 dry-run MR / develop 5건 누적으로 분리 추적.
- **R**: `.gitlab-ci.yml` `+33/-3` 으로 잠재 중복 실행 시나리오 3개 → 룰 정의 기준 0개. GHCR에 push될 잠재 MR commit 이미지 태그 매 push당 1개 → 0개. wall-clock 절감 정량은 후속 측정 카드로 분리 추적(직전 카드 2차 실측 10m 53s 기준 머지당 ~10m+ 절감 기댓값). 5/15 Code Freeze DoD "머지 후 30분 내 빌드 완료" 마진을 직전 카드의 -64% 위에서 추가로 확보.

### 5/22 발표 4축 매핑

- **성과 측정** ← 본 카드 (CI 비용 / 중복 실행 정리는 DoD 마진 확장 + GHCR 운영 비용 절감)
- **멀티테넌트** ← 직접 매핑 없음
- **비동기** ← 직접 매핑 없음
- **채널 추상화** ← 직접 매핑 없음
