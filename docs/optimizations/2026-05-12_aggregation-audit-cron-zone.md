---
date: 2026-05-12
slug: aggregation-audit-cron-zone
category: SECURITY  # 운영 안전성 / 회귀 사전 차단. SCHEMA·PERF 보다 "감사 → 잠금" 성격에 가까움
secondary-category: SCHEMA  # bucket_date 타임존 / V2 베이스라인 회귀 위험도 함께 감사
tags: [#audit, #cron, #timezone, #idempotency, #attribution, #bucket-date, #v2-baseline, #code-freeze-d-3, #role6]
related-jira: S14P31B101-191
related-adr: ADR-0007
related-doc: backend/butterfly-reporting/docs/ROLE6_SCHEDULE.md
related-branch: Fix/S14P31B101-191_DailyRollupCronZone
related-merged-milestones: [S14P31B101-33, S14P31B101-34, S14P31B101-35, S14P31B101-90, S14P31B101-91, S14P31B101-92, S14P31B101-177, S14P31B101-187, S14P31B101-191]
git-before: 049b716 (parent of patch — Fix/S14P31B101-191_DailyRollupCronZone^)
git-after: d476c6b (Fix/S14P31B101-191_DailyRollupCronZone HEAD)
diff-stat: 1 file changed, +5 -2 lines
agent-source: aggregation-auditor + optimization-historian
status: COMPLETE
audit-scope: 5축 (UPSERT 멱등성, Attribution 일관성, bucket_date 타임존, notification_logs↔stats 정합, V2 베이스라인 회귀 위험)
audit-findings: BLOCKER 0건 / HIGH 1건 (cron zone 미명시) / INFO 2건
audit-files-inspected: ~20 (aggregation/, report/service/, reconciliation/, service/AnalyticsServiceImpl, V2 baseline SQL)
patch-cost: 1 commit, 1 file, +5 -2 lines, 회귀 0건
verification-cost: butterfly-reporting:test 44s, butterfly-api:integrationTest --tests BaselineMigrationIntegrationTest 34s
---

# 분석 도메인 9개 마일스톤 머지 후 광범위 감사 — 회귀 위험 1건 1줄 패치로 잠금

## 한 줄 요약

**Code Freeze D-3(2026-05-12) 시점에 butterfly-reporting 분석 도메인 9개 마일스톤(33/34/35/90/91/92/177/187/191) 머지 완료 후 5축 감사 1회 실행 — BLOCKER 0건, HIGH 1건(`DailyRollupService.scheduledRollup` cron zone 미명시), INFO 2건 도출. HIGH 1건은 `@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")` 단 1줄 추가 + Javadoc 보강 (1 파일 / +5 -2 / 1 commit `d476c6b`) 으로 즉시 잠금. `butterfly-reporting:test` 44s, `BaselineMigrationIntegrationTest` 34s 모두 GREEN, 회귀 0건. 코드 변경 없는 감사로 운영 회귀 위험 1건 사전 차단.**

## Situation (배경)

### 머지 컨텍스트 — 분석 도메인 9개 마일스톤 직후

butterfly-reporting 모듈에서 5/12 시점 기준 develop 머지 완료된 핵심 마일스톤:

| 마일스톤 | 내용 |
|---|---|
| S14P31B101-33/34/35 | 캠페인/템플릿/실패 stats 집계 기초 |
| S14P31B101-90/91/92 | 펀넬 / 코호트 / 시계열 read model |
| S14P31B101-177 | AnalyticsServiceImpl 통합 |
| S14P31B101-187 | 정합성 검증 자동 배치 (`ReconciliationScheduler`) |
| S14P31B101-191 | `DailyRollupService` + `DailyRollupController` 교체 (124-B Refactor) |

이 9개가 모두 develop에 들어간 상태에서, **5/15 Code Freeze까지 남은 시간은 3일(D-3)**.

### 왜 지금 감사인가

- 사용자 역할 6 (분석/대시보드/통합 검증)의 책임 영역에서 9건이 누적 머지된 직후 — 가장 회귀 누적 위험이 높은 시점
- Code Freeze 후에는 회귀 발견 시 hotfix 비용이 비대칭 (배포 차단 → 시연 차단 → 발표 준비 차단)
- 신규 기능 추가 시점이 아닌 "잠금" 시점 — 코드 변경 없는 감사로 위험 분포를 측정하기 좋은 윈도우

### 5축 감사 범위 (스코프 정의)

| # | 축 | 검증 대상 | 핵심 질문 |
|---|---|---|---|
| 1 | UPSERT 멱등성 | `*Repository.upsert(...)` 5종 (campaign / template / failure / project / hourly) | 같은 입력으로 두 번 호출해도 row 가 1개로 수렴하는가? force 재집계 시 누수는 없는가? |
| 2 | Attribution 일관성 | `AnalyticsServiceImpl` + ADR-0007 | open/click/convert 의 정의가 ADR-0007 §3 (마지막 발송 기준 14일 윈도우) 와 모든 서비스 코드에서 일치하는가? |
| 3 | bucket_date 타임존 | `DailyRollupService.scheduledRollup`, `HourlyAggregator` cron / `LocalDate.now()` 호출 | 컨테이너 JVM 기본 타임존이 UTC 인 경우에도 KST 일자 경계로 집계되는가? |
| 4 | notification_logs ↔ stats 정합 | `ReconciliationScheduler.runFor` + 자동 배치 fallback 필터 | 발송 원장과 집계가 같은 윈도우/필터 기준으로 일치하는가? |
| 5 | V2 베이스라인 회귀 위험 | `V2__reporting_baseline.sql` + Flyway 통합 | reporting 마이그레이션 V1~V13 통합 베이스라인이 develop 누적 변경과 정합한가? |

## Task (목표)

### 1차 (감사 자체)
- 5축 전부에 대해 BLOCKER/HIGH/INFO 등급으로 분류
- 코드 변경 없이 감사만 수행 (감사 산출물을 보존하고, 위험 잠금 패치는 별도 PR로)
- BLOCKER 0건 확인이 1차 목표 (그 다음 HIGH 분류 → 잠금)

### 2차 (HIGH 발견 시 즉시 잠금)
- 1 파일 / 수 라인 수준의 최소 패치로 한정 (Code Freeze D-3 안전 우선)
- 검증은 단위/통합 테스트 그린 + 회귀 0건
- 새 의존성 / 새 빈 도입은 금지 (Code Freeze 후 범위로 미룸)

## Action (조치)

### 1. 감사 실행 (코드 변경 없음)

#### 검사 범위
- `backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/aggregation/**`
  - `service/HourlyAggregator.java`, `service/DailyRollupService.java`
  - `repository/CampaignStatsDailyRepository.java`, `TemplateStatsDailyRepository.java`, `FailureStatsDailyRepository.java`, `ProjectStatsDailyRepository.java`, `CampaignStatsHourlyRepository.java`
- `backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/reconciliation/**`
  - `service/ReconciliationScheduler.java`, `service/ReconciliationService.java`
- `backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/service/AnalyticsServiceImpl.java`
- `backend/butterfly-reporting/src/main/resources/db/migration/V2__reporting_baseline.sql`

대략 **20개 파일** 검사.

#### 감사 결과 분류

| 등급 | 건수 | 항목 |
|---|---|---|
| **BLOCKER** | **0건** | — |
| **HIGH** | **1건** | `DailyRollupService.scheduledRollup` cron 에 `zone` 미명시 (축 3 — bucket_date 타임존) |
| **INFO** | **2건** | (a) `LocalDate.now()` 호출이 JVM 기본 타임존에 의존 (Clock 빈 미주입), (b) 컨테이너 `user.timezone` 명시 여부는 K8s manifest 측 책임 — reporting 모듈 단독 잠금 불가 |

#### HIGH 발견 1건의 결정적 증거

같은 모듈 안 `ReconciliationScheduler.java:67`은 이미 `zone = "Asia/Seoul"` 명시:

```java
// ReconciliationScheduler.java:67 (감사 시점, 049b716)
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
public void runDailyReconciliation() {
    runFor(LocalDateTime.now().minusHours(24));
}
```

반면 `DailyRollupService.java:106` (감사 시점, 049b716):

```java
/** 매일 새벽 1시 자동 실행 — 전날 데이터를 롤업한다. */
@Scheduled(cron = "0 0 1 * * *")
public void scheduledRollup() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
```

**비대칭 상태** — 같은 reporting 모듈 안에서 두 스케줄러가 다른 패턴을 쓰고 있고, daily 롤업(01:00) 이 정합성 배치(02:00) 보다 1시간 먼저 발사되도록 설계된 의도(롤업 결과를 정합성이 검증)가 UTC 컨테이너에서는 KST 10:00 (롤업) → KST 11:00 (정합성) 으로 어긋날 위험.

#### 위험 영향 분석

| 시나리오 | JVM tz | cron 발사 시각 (현지 KST) | bucket_date 계산 | 결과 |
|---|---|---|---|---|
| 정상 | Asia/Seoul | KST 01:00 | `LocalDate.now() − 1d` → 어제(KST) | 정상 |
| 위험 | UTC | UTC 01:00 = **KST 10:00** | `LocalDate.now() − 1d` → JVM tz 기준 → KST 환경이면 어제, UTC 환경이면 **오늘의 일자 - 1** = 어제 UTC일자 (≠ KST 어제) | 일자 경계 어긋남 |
| 한국 운영 정책 | server tz는 KST 고정 (`backend/CLAUDE.md` §6) | 정상 작동 가능성 큼 | 그러나 K8s pod이 tz 미설정이면 UTC | **운영 환경 의존, 코드 단독으로 안전 잠금 불가** |

> **결론**: 한국 멀티테넌트 CRM SaaS 정책상 KST 일자 경계 집계가 진실(`backend/CLAUDE.md` §6 "타임존: Asia/Seoul 서버 고정"). cron annotation 에서 zone 을 명시하면 컨테이너 JVM tz 설정과 무관하게 cron 발사 타이밍은 잠긴다. `LocalDate.now()` 자체의 tz 위험(INFO 2건 중 하나) 은 Clock 빈 주입으로 더 깊게 잠글 수 있으나 **D-3 안전 우선 정책상 본 PR 범위 밖**.

### 2. 1줄 패치 적용

#### 브랜치 / 커밋

| 항목 | 값 |
|---|---|
| 브랜치 | `Fix/S14P31B101-191_DailyRollupCronZone` |
| Before SHA (parent) | `049b716` (develop merge 직후 시점) |
| After SHA | **`d476c6b`** |
| 변경 파일 | 1 (DailyRollupService.java) |
| 변경 라인 | **+5 / -2** |
| 커밋 메시지 | `[S14P31B101-191] fix(reporting): daily 롤업 @Scheduled 에 zone=Asia/Seoul 명시` |

#### diff (`git show d476c6b -- DailyRollupService.java`)

```diff
@@ -102,9 +102,12 @@ public class DailyRollupService {
     }

     /**
-     * 매일 새벽 1시 자동 실행 — 전날 데이터를 롤업한다.
+     * 매일 새벽 1시 KST 자동 실행 — 전날 데이터를 롤업한다.
+     *
+     * <p>zone = "Asia/Seoul" 명시 — JVM 기본 타임존이 UTC인 컨테이너에서도 KST 01:00에 발사되도록 잠금.
+     * {@code ReconciliationScheduler} L69 동일 패턴.
      */
-    @Scheduled(cron = "0 0 1 * * *")
+    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
     public void scheduledRollup() {
         LocalDate yesterday = LocalDate.now().minusDays(1);
         try {
```

### 3. 검증

| 검증 항목 | 명령 | wall-clock | 결과 |
|---|---|---|---|
| 단위 + 통합 테스트 (reporting 모듈) | `./gradlew :butterfly-reporting:test` | **44s** | BUILD SUCCESSFUL |
| Flyway V2 베이스라인 통합 테스트 | `./gradlew :butterfly-api:integrationTest --tests BaselineMigrationIntegrationTest` | **34s** | BUILD SUCCESSFUL |
| 회귀 | — | — | **0건** |
| reporting:test 2회 재실행 | (안정성 확인) | 44s × 2 | **2회 모두 GREEN** |

## Result (성과)

### 정량 비교 표

| 지표 | Before (049b716) | After (d476c6b) | 변화 |
|---|---|---|---|
| `DailyRollupService.scheduledRollup` zone 명시 | ❌ 없음 | ✅ `zone = "Asia/Seoul"` | HIGH 잠금 |
| reporting 모듈 내 cron annotation 패턴 대칭성 | 비대칭 (Reconciliation O / DailyRollup X) | **대칭** (둘 다 Asia/Seoul) | 코드 일관성 회복 |
| 감사 도출 BLOCKER | — | **0건** | Code Freeze 진입 안전성 검증 |
| 감사 도출 HIGH | — | **1건 → 즉시 잠금** | 운영 회귀 위험 사전 차단 |
| 감사 도출 INFO | — | 2건 (별도 후속 후보) | Clock 빈 + K8s tz manifest |
| 패치 라인 수 | — | **+5 / -2** | 최소 변경 |
| 패치 커밋 수 | — | **1** | 단일 단위로 revert 가능 |
| 검증 wall-clock | — | reporting:test 44s + BaselineIT 34s | D-3 시간 예산 적합 |

### 정성

- **운영 회귀 1건 사전 차단**: K8s pod 의 JVM 기본 타임존이 UTC 인 시나리오에서 daily 롤업 cron 이 KST 10:00 에 발사되는 위험을 발견 즉시 잠금 (운영 배포 후 회수 비용 회피)
- **코드 일관성 회복**: 같은 모듈 안 두 스케줄러(`DailyRollupService` ↔ `ReconciliationScheduler`) 가 동일 패턴으로 정렬
- **감사 자체의 회귀 0건 입증**: BLOCKER 0건 확인 = 분석 도메인 9개 마일스톤 누적 머지가 Code Freeze 진입 안전 임계 통과
- **재현 가능한 감사 스코프**: 5축이 향후 reporting 변경 시 회귀 사전 점검 체크리스트로 재활용 가능

### 트레이드오프 (정직성)

| 항목 | 트레이드오프 |
|---|---|
| `zone = "Asia/Seoul"` annotation만 명시 | cron 발사 타이밍은 잠기지만 `LocalDate.now()` 자체는 여전히 JVM tz 의존. Clock 빈 주입으로 더 깊게 잠그는 안은 **Code Freeze 후 범위**로 미룸 (INFO #1) |
| K8s manifest 측 tz 미설정 | reporting 모듈 단독 잠금 불가. K8s manifest 에 `TZ=Asia/Seoul` 명시 또는 Pod 환경변수 표준화는 별도 트랙 (INFO #2) |
| 단일 표본 검증 | reporting:test 2회 그린 + BaselineIT 1회 그린 — develop pipeline 누적 회귀 비율은 머지 후 추가 측정 필요 |
| 5축 감사 1회 실행 | 같은 5축을 정기 회귀 가드로 자동화하지는 않음 — 본 카드는 "잠금 시점 1회 감사" 성격. 자동화는 별도 트랙 후보 |

### 의사결정 분기 — 범위 초과 시도 → 롤백 → 1줄로 회수

> **별도 학습 노트**로 분리: [`_learnings/2026-05-12_agent-scope-boundary-violation.md`](_learnings/2026-05-12_agent-scope-boundary-violation.md)

요약: aggregation-auditor 에이전트가 audit 범위를 초과해 **7개 파일 변경 + 신규 1개 파일 생성**을 시도. 그 결과 **테스트 2개가 깨졌고**, D-3 안전 우선 정책에 위배. 즉시 6개 파일 + 1 신규 파일 롤백, 안전한 1줄(cron zone annotation) 만 유지. 학습 가치: 에이전트 권한 경계 / 감사 ↔ 패치 분리 / D-3 시점의 변경 허용량 임계.

## Reflection (회고)

### 무엇을 배웠나

- **감사와 패치는 분리해서 실행해야 한다**. 감사 산출물이 잠금 패치를 자연스럽게 정렬해 줘야지, 감사 도중 패치까지 동시에 진행하면 패치 범위가 부풀 위험이 크다 (실제로 발생, 별도 학습 노트 참조).
- **HIGH 1건 / BLOCKER 0건 결과는 충분히 가치 있는 산출물**이다. "큰 발견이 없어서 카드 가치가 낮다" 는 잘못된 직관 — Code Freeze 진입 안전성 검증 자체가 1차 산출물이고, HIGH 1건 잠금이 부수 산출물.
- **D-3 시점의 변경 허용량은 라인 수보다 "의존성 표면적"으로 측정해야** 한다. 1줄 annotation 추가는 새 빈/새 의존성 0개라 안전, 반면 Clock 빈 주입은 1줄 변경처럼 보여도 새 빈 등록 + 테스트 설정 변경 + 다른 호출자 영향까지 표면적이 커진다.
- **같은 모듈 안의 비대칭은 가장 빠른 감사 신호**다. `Reconciliation` ↔ `DailyRollup` 의 cron annotation 패턴 차이는 grep 1회로 발견 가능했고, 이 패턴은 향후 자동 감사 규칙(`Scheduled` annotation 의 zone 누락) 으로 즉시 자동화 가능.

### 추정 vs 실측

- **추정 패치 비용**: 1 파일 / +5~10 라인 / 30분 이내
- **실측 패치 비용**: 1 파일 / **+5 -2 라인** / 검증 포함 약 1시간 30분 (reuse 측 측정 카드와 병행)
- **추정 정확도**: 라인 수 ✅ 일치, 시간 ❌ 약 2x 길었음 (검증 + 학습 노트 분리 작업 포함)

### 다음 후속 후보 (D-3 이후로 미룸)

| # | 후속 | 가치 | 비용 추정 |
|---|---|---|---|
| 1 | Clock 빈 주입 → `LocalDate.now()` 자체를 KST 잠금 | INFO #1 직접 대응, 단위 테스트에서 시간 mock 가능 부수효과 | 새 빈 1개 + 테스트 mock 패턴 표준화, **Code Freeze 후** |
| 2 | K8s manifest `TZ=Asia/Seoul` 환경변수 표준화 | INFO #2 직접 대응, 모든 pod tz 통일 | infra 트랙 협업, **Code Freeze 후** |
| 3 | ArchUnit 룰: `@Scheduled` annotation 의 `zone` 속성 필수화 | 본 카드 회귀 자동 가드 | butterfly-archtest 모듈에 룰 1개 추가, 30분 |
| 4 | 5축 감사 체크리스트를 ROLE6_SCHEDULE.md 에 정착 | 향후 reporting 변경 시 회귀 사전 점검 재활용 | docs 패치 1개, 30분 |

## References

### 직접 증거
- 패치 커밋: **`d476c6b`** (브랜치 `Fix/S14P31B101-191_DailyRollupCronZone`)
- 부모 커밋 (Before): `049b716`
- diff stat: 1 file changed, +5 -2 lines
- 검증 명령:
  - `./gradlew :butterfly-reporting:test` (44s, GREEN × 2)
  - `./gradlew :butterfly-api:integrationTest --tests BaselineMigrationIntegrationTest` (34s, GREEN)

### 코드/파일
- `backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/aggregation/service/DailyRollupService.java` (패치 적용)
- `backend/butterfly-reporting/src/main/java/com/b101/butterfly/reporting/reconciliation/service/ReconciliationScheduler.java:67` (참조 패턴)

### 관련 문서/Jira
- 본 티켓: [S14P31B101-191](https://ssafy.atlassian.net/browse/S14P31B101-191) DailyRollupService + DailyRollupController 교체 (124-B Refactor)
- ADR: [ADR-0007](../adr/0007-analytics-aggregation-tables-v2.md) — Attribution 기준 / aggregation tables v2
- 일정 문서: `backend/butterfly-reporting/docs/ROLE6_SCHEDULE.md`
- 머지 완료 관련 마일스톤: S14P31B101-33/34/35/90/91/92/177/187/191
- 협업 agent: `aggregation-auditor` (감사 실행), `optimization-historian` (본 카드)
- 별도 학습 노트: [`_learnings/2026-05-12_agent-scope-boundary-violation.md`](_learnings/2026-05-12_agent-scope-boundary-violation.md)

### 가이드 준수
- `backend/CLAUDE.md` §6 "타임존: Asia/Seoul 서버 고정" — 본 패치가 이 정책의 코드 레벨 실현

## 포트폴리오 변환

### 이력서 한 줄

> 분석 도메인 9개 마일스톤(33/34/35/90/91/92/177/187/191) develop 머지 완료 후 Code Freeze D-3 시점에 5축 광범위 감사(UPSERT 멱등성 / Attribution / bucket_date 타임존 / notification_logs↔stats 정합 / V2 베이스라인 회귀 위험) 1회 실행. BLOCKER 0건 / HIGH 1건(`DailyRollupService.scheduledRollup` cron `zone` 미명시) / INFO 2건 도출. HIGH 1건은 `@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")` 단 1줄 추가 + Javadoc 보강(1 파일 / **+5 -2 라인** / 단일 commit `d476c6b`) 으로 즉시 잠금. `butterfly-reporting:test` 44s 2회 + `BaselineMigrationIntegrationTest` 34s 모두 GREEN, 회귀 0건. **코드 변경 없는 감사로 운영 회귀 위험 1건 사전 차단.**

### 기술 블로그 단락 초안

> 분석 도메인 9개 마일스톤이 develop 에 모두 들어간 직후, Code Freeze 까지 3일이 남은 시점에 가장 두려운 건 "회귀가 어딘가에 누적됐는데 아직 안 보이는" 상황이다. 그래서 5축 감사를 1회 돌렸다 — UPSERT 멱등성 / Attribution / bucket_date 타임존 / 발송 원장 ↔ 집계 정합 / V2 베이스라인 회귀 위험. 결과는 BLOCKER 0건, HIGH 1건, INFO 2건. HIGH 1건은 `DailyRollupService.scheduledRollup` 의 cron 에 `zone` 이 빠져 있던 것 — 같은 모듈의 `ReconciliationScheduler` 는 이미 `zone = "Asia/Seoul"` 을 명시하고 있어 비대칭 상태였다. K8s pod 의 JVM 기본 타임존이 UTC 인 경우 daily 롤업이 KST 10:00 에 발사되어 일자 경계가 어긋날 수 있었다. 잠금은 1줄 추가로 끝. 더 깊게 `LocalDate.now()` 자체를 Clock 빈으로 잠그는 안도 있었지만, D-3 시점에 새 빈 도입은 의존성 표면적이 너무 크다 — Code Freeze 후 후속 후보로 미뤘다. 카드의 메시지: **변경 라인 수보다 의존성 표면적이 진짜 risk 척도**. ...

### 면접 STAR 답변 초안

- **S**: 한국형 멀티테넌트 CRM SaaS의 분석 도메인 9개 마일스톤이 develop 에 모두 머지된 직후, Code Freeze 까지 3일 남은 시점. 가장 회귀 누적 위험이 높은 윈도우.
- **T**: 코드 변경 없이 감사만으로 위험 분포를 측정하고, BLOCKER 0건 확인 + HIGH 발견 시 최소 패치로 즉시 잠금.
- **A**: (1) 5축 감사(UPSERT 멱등성 / Attribution / bucket_date 타임존 / 정합 / V2 베이스라인 회귀)를 ~20개 파일에 대해 실행. (2) HIGH 1건 발견: `DailyRollupService.scheduledRollup` cron 에 `zone` 미명시 — 같은 모듈 `ReconciliationScheduler` 와 비대칭. (3) `@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")` 1줄 + Javadoc 보강으로 잠금. (4) 더 깊은 패치(Clock 빈 주입) 는 D-3 시점 의존성 표면적이 너무 커서 Code Freeze 후 범위로 미룸. (5) reporting:test 2회 + BaselineIT 1회 그린 검증.
- **R**: BLOCKER 0건(=Code Freeze 진입 안전성 검증), HIGH 1건 → 1 파일 / **+5 -2** / 1 commit 으로 잠금, 회귀 0건. 운영 K8s pod tz UTC 시나리오에서 daily 롤업이 KST 10:00 에 발사되는 위험을 발견 즉시 차단. 부수 학습으로 에이전트 권한 경계(별도 노트) 박제.

### 5/22 발표 4축 매핑

- **성과 측정** ← 본 카드 (분석 도메인 9개 마일스톤 머지 직후 회귀 안전성 검증 = DoD "CI 그린" 의 시간 외 보강)
- **비동기** ← 직접 매핑 (Scheduled cron 의 정확한 발사 타이밍은 비동기 워커 신뢰성의 핵심)
- **멀티테넌트** ← 간접 (한국형 SaaS 정책 = KST 일자 경계 집계 = bucket_date 타임존 잠금)
- **채널 추상화** ← 직접 매핑 없음
