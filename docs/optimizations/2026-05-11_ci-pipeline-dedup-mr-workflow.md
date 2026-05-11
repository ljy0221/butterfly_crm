---
date: 2026-05-11
slug: ci-pipeline-dedup-mr-workflow
category: DX
tags: [#ci, #gitlab, #mr-workflow, #pipeline-dedup, #cost-saving]
status: VERIFIED
related-jira: S14P31B101-220
related-followup: [S14P31B101-224, S14P31B101-226, S14P31B101-63]
mr: !137
git-before: 8b1d710
git-after-mr-extra: 8640713
git-merge: 4193187
git-after-develop: 6b548580
agent-source: optimization-historian
---

# GitLab CI 파이프라인 중복 제거 — MR workflow + interruptible + package when:never

## 한 줄 요약
**workflow rules + interruptible + package when:never 3종 결합으로 MR이 열린 브랜치의 동시 파이프라인을 2개에서 1개로 줄이고, MR pipeline의 잡을 5개에서 4개로 축소해 GHCR 태그 오염을 차단했다. MR !137에서 dry-run 검증 완료, develop 머지 후 package 잡(1m 59s) 정상 동작 확인.**

---

## Situation (배경)

butterfly 프로젝트의 GitLab CI는 한 commit이 push되면 두 종류의 파이프라인을 동시에 트리거하는 구조였다.

- branch pipeline: `$CI_COMMIT_BRANCH`로 트리거되어 unit-test/integration-test/checkstyle/package 전부 실행
- MR pipeline: MR이 열려 있으면 `merge_request_event`로 별도 트리거되어 동일 잡 다시 실행

결과:

- 같은 commit에 대해 **잡 2배 중복 실행** (runner 비용 + 결과 가독성 저하)
- MR pipeline에서도 `package` 잡이 돌면서 **GHCR에 MR commit SHA 태그를 push** → 머지되지 않는 임시 commit이 레지스트리를 오염
- 이전 commit의 잡이 끝나기도 전에 다음 commit이 push되어 cancel 없이 누적 (interruptible 미설정)

5/15 Code Freeze + 5/22 시연을 앞두고 CI 자원·신뢰도를 정리할 필요가 있었다.

## Task (목표)

1. MR이 열린 브랜치에 push 시 **branch pipeline을 차단**하고 MR pipeline만 실행
2. MR pipeline에서는 **package 잡 스킵** (GHCR 태그 오염 차단)
3. MR에 추가 commit 시 **이전 pipeline 자동 캔슬** (interruptible)
4. develop/main 머지 commit은 **branch pipeline 1회만 실행** (통합 검증 + package)

## Action (조치)

### 진단 (Before — 2026-05-11 작업 시작 직전, commit `8b1d710`)

**문제 구조 (Phase 11까지의 .gitlab-ci.yml)**:

- `workflow:` 블록 부재 → branch + MR 양쪽 트리거
- `default: interruptible:` 부재 → 캔슬 정책 없음
- `package` 잡 rules에 `merge_request_event` 차단 없음 → MR마다 GHCR push

**관측 증거**:

- MR을 하나 열면 GitLab UI에 commit별로 pipeline 2개씩 표시 (branch 1 + MR 1)
- GHCR `ghcr.io/ljy0221/butterfly-api`에 MR commit SHA 태그가 누적

### 적용 (After — `.gitlab-ci.yml` 변경, commit `8b1d710` → 머지 `4193187`)

**1. workflow rules 추가** (line 16~21):

```yaml
workflow:
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
      when: never
    - if: $CI_COMMIT_BRANCH
```

규칙 평가 순서:
- MR 이벤트면 → MR pipeline 통과
- 그게 아니고 branch push인데 그 브랜치에 열린 MR이 있으면 → 차단
- 그 외 일반 branch push (MR 없음 / develop / main 머지) → branch pipeline 통과

**2. interruptible 글로벌 기본값** (line 8~9):

```yaml
default:
  interruptible: true
```

→ MR에 추가 commit push 시 이전 pipeline의 모든 잡이 자동 캔슬.

**3. package 잡에 MR 차단 규칙** (line 174~176):

```yaml
package:
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
      when: never
    - if: $CI_COMMIT_REF_NAME == "main"
    - if: $CI_COMMIT_REF_NAME == "develop"
    ...
```

→ MR pipeline에서는 package 스킵. develop/main 머지 commit에서만 GHCR push.

---

## Result (성과 — 2026-05-11 검증 완료)

### 검증 1. MR !137 dry-run (S14P31B101-220 본 PR로 직접 검증)

| 검증 항목 | 기대 | 실측 | 결과 |
|---|---|---|---|
| MR 생성 직후 동시 pipeline 수 | 1 (Detached MR pipeline만) | 1 | ✓ |
| MR pipeline 잡 매트릭스 | unit-test, integration-test, checkstyle, frontend-build | 동일 4개 | ✓ |
| MR pipeline에서 package 잡 | 실행되지 않음 (when: never) | 미실행 | ✓ |
| MR 추가 commit push 시 이전 pipeline | 자동 캔슬 | `8b1d710` MR pipeline 캔슬, 새 `8640713` MR pipeline 1개 | ✓ |
| branch pipeline 동시 실행 건수 | 0 (workflow rule로 차단) | 0 | ✓ |

증거: MR !137 GitLab UI pipeline 목록 (commit `8b1d710` → `8640713` 캔슬 흐름).

### 검증 2. develop 머지 후 package 잡 정상 실행

- 머지 commit: `4193187` (Merge branch 'Refactor/S14P31B101-220_CIWorkflowDedup' into 'develop')
- develop SHA: `6b548580`

**package 잡 timing (wall-clock 1m 59s)**:

| 단계 | 소요 |
|---|---|
| cache restore | 14s |
| script (`./gradlew :butterfly-api:jib --build-cache --no-daemon`) | 1m 5s |
| cache save | 31s |
| **합계** | **1m 59s** |

- Jib build 성공: `ghcr.io/ljy0221/butterfly-api`에 `:6b548580`, `:develop`, `:latest` 태그 push
- 모든 `compileJava` 9개 모듈 FROM-CACHE (Gradle Build Cache 효과 누적 확인)

### 정량 요약

| 지표 | Before | After | 개선 |
|---|---|---|---|
| MR 1건당 동시 pipeline 수 | 2 (branch + MR) | 1 (MR만) | **50% ↓** |
| MR pipeline 잡 수 | 5 (test 4 + package 1) | 4 (test 4) | **20% ↓** |
| MR 추가 commit 시 이전 pipeline 정리 | 누적 (수동 캔슬 필요) | 자동 캔슬 | 운영 부담 0 |
| GHCR MR commit SHA 태그 push | 매 push마다 | 0 | **100% ↓** |
| develop 머지 시 package 동작 | 정상 | 정상 (1m 59s) | 회귀 없음 |

### 정성

- 시연 직전 GHCR 레지스트리 정리 부담 감소
- MR review 시 pipeline 결과 가독성 향상 (1개만 보면 됨)
- `interruptible: true`로 빠른 iteration 시 runner 자원 낭비 차단

### 트레이드오프

- **MR pipeline에서 package를 스킵 = MR 단계에서는 이미지 빌드 검증 누락**. develop 머지 직후 package 실패 시 hotfix 흐름 필요. 현재는 `unit-test` 단계에서 컴파일 검증되므로 큰 위험 없음
- workflow rules 평가 순서를 잘못 짜면 일부 push가 아예 트리거되지 않을 수 있음 (실제로 MR !137 dry-run에서 검증)

---

## 추가 발견 — 이번 작업에서 파생된 부수 효과

이번 dry-run 검증과 develop package 잡 측정 과정에서 추가로 드러난 4가지 개선 후보. 220 본 작업의 범위는 아니지만 후속 액션으로 발굴됨.

### A. integration-test가 진짜 critical path

- package 잡은 1m 59s로 짧다. CI 전체 wall-clock의 병목은 **integration-test**일 가능성이 높음 (별도 측정 필요)
- Testcontainers MySQL을 4개 모듈(api/event/campaign/reporting)이 차례로 띄우는 구조 → 컨테이너 startup이 누적
- **후속 액션**: S14P31B101-226 (testcontainers reuse, `TESTCONTAINERS_REUSE_ENABLE=true`) 작업 진행 중. 30~60초 단축 목표
- 측정 우선: `integration-test` 잡 wall-clock breakdown (컨테이너 startup vs 테스트 실행 vs cache I/O)

### B. GitLab cache 정책 비효율

- 현재 4개 잡(unit-test, integration-test, checkstyle, package)이 모두 같은 cache key `shared-...`를 `policy: pull-push`로 공유 (`.gitlab-ci.yml` line 40~50)
- package 잡은 빌드 산출물을 jib이 직접 GHCR에 push하므로 **gradle cache를 갱신할 이유 없음** → 31s 들여 cache push 중
- 동일 commit에서 4개 잡이 동시에 같은 cache를 push → **race condition** 위험 (덮어쓰기로 일부 잡의 캐시 갱신이 무효화될 가능성)
- **후속 액션 후보**: package 잡만 `policy: pull`로 변경 → 약 30초 단축 + race 제거. 별도 티켓 발급 권장
- 추가 검토: checkstyle도 컴파일 cache만 read하므로 `policy: pull` 후보

### C. 이미지 태그 전략 약점

- 매 develop 머지마다 `:6b548580`, `:develop`, `:latest` 3종이 함께 push됨 (`backend/build.gradle` line 116~122)
- `:develop` 별칭은 환경 고정용으로 OK
- `:latest`는 운영 신뢰도가 낮음. develop 머지마다 즉시 갱신되어 **롤백 시 기준점 모호**, 외부에서 `:latest` pull이 의도치 않게 develop을 가리킴
- jib 설정 위치: **`backend/build.gradle` (루트, line 98~127)**. 사용자 문의의 `butterfly-api/build.gradle`이 아니라 모든 bootJar 모듈에 공통 적용되는 루트 build script
- **후속 액션 후보**: S14P31B101-63 (분리 배포/롤백 런북) 티켓에 흡수 가능
  - `:develop`만 alias로 유지, `:latest`는 main 머지에서만 push
  - 또는 `:develop-stable` 별칭 도입 (수동 promotion)

### D. cgroup limit 부재 (S14P31B101-224 측정으로 발견)

- 운영 환경: k3s on EC2 (k3d-temp-dev runner, 노드 ip-172-26-1-159, 15GiB / 4 core)
- **K8s pod에 `resources.limits.memory` 설정 없음** — 컨테이너가 노드 메모리(15GiB)를 자유 경쟁
- 과거 통합 테스트의 `OOMKilled exit 137`의 진짜 원인 후보 (현재는 `--no-parallel`과 `workers.max=2`로 우회 중)
- **후속 액션 후보**: G안 신규 티켓 발급 권장
  - K8s runner config (`config.toml` 또는 GitLab Runner Helm values)에 `memory_limit` / `memory_swap` / `cpu_limit` 명시
  - 한도 명시 후 `--no-parallel` 제거 / `workers.max` 복원 / 모듈별 matrix 분할 등 병렬화 재시도
  - ci-diagnostics 잡(`.gitlab-ci.yml` line 250~284)이 이미 cgroup 정보를 dump하도록 준비됨

---

## Reflection (회고)

- workflow rules는 평가 **순서**가 결정적이다. 추측 대신 dry-run MR로 검증한 게 정답이었다 (MR !137이 자기 자신을 테스트)
- "잡을 줄였다"보다 "GHCR 태그 오염 0건" 같은 운영 신뢰도 지표가 더 와닿는다
- 한 PR에서 의도한 개선(220) 외에 **3건의 후속 티켓 후보(B/C/D)** 가 발굴됨. 측정/검증을 끼워 넣은 PR은 이런 부산물을 잘 만든다
- 추정 vs 실측: package 잡 추정 wall-clock "2~3분" → 실측 1m 59s. 추정 정확도 ✓

## References

- MR: https://lab.ssafy.com/.../-/merge_requests/137
- 코드: `.gitlab-ci.yml` (workflow line 16~21, default line 8~9, package rules line 173~186)
- jib 태그 설정: `backend/build.gradle` line 98~127
- 진단 잡: `ci-diagnostics` (`.gitlab-ci.yml` line 250~284)
- 관련 티켓:
  - 본: S14P31B101-220 (CI workflow dedup)
  - 후속: S14P31B101-226 (testcontainers reuse), S14P31B101-224 (cgroup 측정)
  - 흡수 후보: S14P31B101-63 (분리 배포/롤백 런북)
- 운영 환경: k3s on EC2 (k3d-temp-dev runner, ip-172-26-1-159, 15GiB / 4 core)

---

## 포트폴리오 변환

### 이력서 한 줄

> GitLab CI workflow rules + interruptible + package when:never 3종 결합으로 MR 1건당 동시 파이프라인을 2→1, MR pipeline 잡을 5→4로 축소하고 임시 commit의 GHCR 태그 push를 100% 차단. dry-run으로 검증한 후 develop 머지 후 package 잡 1m 59s 정상 동작 확인.

### 기술 블로그 단락

> 멀티모듈 백엔드를 GitLab CI에서 빌드할 때, MR이 열린 브랜치에 push하면 branch pipeline과 MR pipeline이 동시에 도는 중복이 골치였다. 더 큰 문제는 MR pipeline의 package 잡이 GHCR에 임시 commit 태그를 push해 레지스트리를 오염시키는 것. workflow의 `$CI_OPEN_MERGE_REQUESTS && when: never`로 branch pipeline을 차단하고, package 잡의 `merge_request_event` 규칙에 `when: never`를 명시하고, `default: interruptible: true`로 추가 commit 시 이전 pipeline을 자동 캔슬했다. 본 PR(MR !137)을 그대로 dry-run에 사용해 의도한 4개 잡만 도는지 확인했고, 머지 후 develop의 package 잡이 1m 59s로 정상 동작했다. 부수 효과로 cache 정책 비효율, 이미지 태그 전략 약점, K8s pod의 cgroup limit 부재 등 후속 후보 3건을 추가 발굴했다.

### 면접 STAR 답변

- **S (Situation)**: GitLab CI에서 MR이 열린 브랜치에 push하면 branch pipeline과 MR pipeline이 동시에 돌아 잡이 2배로 실행되고, MR pipeline의 package 잡이 GHCR에 임시 commit 태그를 push해 레지스트리가 오염되는 문제가 있었습니다.
- **T (Task)**: MR이 있는 브랜치에서는 MR pipeline 1개만 돌리고, MR pipeline에서는 이미지 빌드를 스킵하며, MR에 추가 commit이 들어오면 이전 pipeline을 자동 캔슬하도록 만드는 게 목표였습니다.
- **A (Action)**: workflow의 rules에 `$CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS && when: never`로 branch pipeline을 차단하고, package 잡 규칙 첫 줄에 `merge_request_event && when: never`를 두고, `default: interruptible: true`를 글로벌로 켰습니다. 본 PR을 그대로 dry-run에 사용해 검증했습니다.
- **R (Result)**: MR 1건당 동시 pipeline 2→1, MR pipeline 잡 5→4, GHCR 임시 태그 push 100% 차단. 머지 후 develop package 잡 1m 59s 정상. 검증 과정에서 cache 정책, 태그 전략, K8s cgroup 등 후속 개선 후보 3건도 함께 발굴했습니다.

### 5/22 발표 4축 매핑

- **성과 측정** (1차): "검증된 CI 개선" 사례로 인용. dry-run → 머지 후 실측까지 한 흐름으로 보여줄 수 있음
- **DX/운영 신뢰도** (보조): GHCR 레지스트리 위생, runner 자원 절약
