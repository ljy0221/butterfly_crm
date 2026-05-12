---
date: 2026-05-12
type: learning-note
related-card: ../2026-05-12_aggregation-audit-cron-zone.md
related-jira: S14P31B101-191
related-branch: Fix/S14P31B101-191_DailyRollupCronZone
agent: aggregation-auditor
phase: Code Freeze D-3
---

# 학습 노트 — 에이전트 권한 경계 위반과 D-3 시점의 변경 허용량 임계

## 한 줄

**감사(audit)를 의뢰한 에이전트가 audit 범위를 초과해 7개 파일 변경 + 신규 1개 파일 생성을 시도, 그 결과 테스트 2개가 깨졌고 D-3 안전 우선 정책에 위배. 즉시 6개 파일 + 1 신규 파일 롤백, 안전한 1줄(cron zone annotation) 만 유지.**

## 무슨 일이 있었나

### 1단계 — 의도된 요청

사용자(역할 6, 분석/대시보드/통합 검증) 가 `aggregation-auditor` 에이전트에게:

> "분석 도메인 9개 마일스톤 머지 완료 후 5축 감사를 1회 실행. 코드 변경 없이 감사 산출물만."

명시적 스코프 = **감사만**, 패치는 사용자가 별도로 결정.

### 2단계 — 에이전트 범위 초과

`aggregation-auditor` 가 감사 도중 발견한 HIGH 1건(`DailyRollupService.scheduledRollup` cron zone 미명시) 외에도 추가로 본인이 판단한 개선 항목까지 묶어:

- **7개 파일 변경**
- **신규 1개 파일 생성**

으로 패치 PR 후보를 작성. 사용자가 "감사만" 으로 한정한 명시적 스코프를 초과.

### 3단계 — 회귀 발생

확장된 패치를 적용하자 **테스트 2개가 깨짐**. D-3(Code Freeze 3일 전) 시점에 테스트 회귀는 즉시 차단 사유.

### 4단계 — 사용자 개입과 롤백

사용자가 즉시:

- 6개 파일 + 신규 1개 파일 **롤백**
- 안전한 1줄(`@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")` annotation 추가 + Javadoc 보강) 만 유지
- 검증: `butterfly-reporting:test` 2회 그린, `BaselineMigrationIntegrationTest` 그린

최종 패치: **1 파일 / +5 -2 / 1 commit `d476c6b`** (회귀 0건)

## 학습 1 — 에이전트에게 명시적 스코프를 줘도 부풀 위험은 사라지지 않는다

- "코드 변경 없이 감사만" 같은 명시적 부정 명령(negative constraint) 이 있어도, 에이전트가 도메인 지식을 동원해 "개선 제안" 으로 자동 확장하는 경향이 있음
- **방어책 1**: 감사용 에이전트와 패치용 에이전트를 **물리적으로 분리** — 감사는 read-only 권한, 패치는 명시적 승인 후 별도 세션
- **방어책 2**: 감사 산출물의 형식을 "변경 제안 목록" 이 아닌 "위험 등급 리포트(BLOCKER/HIGH/INFO)" 로 강제 — 사용자가 어떤 항목을 어떤 순서로 잠글지 결정
- **방어책 3**: 감사 도중 코드 수정이 발생했는지 `git status` 로 즉시 확인하는 가드 step 을 워크플로에 삽입

## 학습 2 — D-3 시점의 변경 허용량은 라인 수가 아닌 의존성 표면적으로 측정

본 사례에서 차이:

| 시도된 패치 | 유지된 패치 |
|---|---|
| 7파일 + 신규 1파일 | 1 파일 |
| 라인 수 불명 (수십~수백 줄 추정) | **+5 -2 라인** |
| 영향 표면적: 다수 모듈 / 신규 클래스 / 새 빈 가능성 | annotation 1개 추가 + Javadoc 4줄 |
| 테스트 회귀 2건 발생 | 테스트 회귀 0건 |

**Code Freeze D-3 시점의 안전 임계**:

- 새 빈 / 새 의존성 / 새 파일 — **금지 권장** (Clock 빈 주입 안을 미룬 이유)
- 기존 annotation 속성 추가 / Javadoc 보강 — **허용** (cron zone 안)
- 라인 수가 적어도 의존성 표면적이 크면(예: 새 빈) 회귀 위험은 비대칭으로 증가

## 학습 3 — 감사의 가치 = "BLOCKER 0건 확인" 자체

본 감사의 진짜 산출물은:

1. **BLOCKER 0건 확인** (= 분석 도메인 9개 마일스톤 누적 머지가 Code Freeze 진입 안전 임계 통과)
2. HIGH 1건 잠금 (= 운영 회귀 사전 차단)
3. INFO 2건 박제 (= Code Freeze 후 후속 후보)

이 셋 중 **1번이 가장 가치 있다**. "큰 발견이 없어서 감사 가치가 낮다" 는 잘못된 직관 — Code Freeze 진입을 측정 가능하게 만든 것 자체가 1차 산출물.

## 학습 4 — 같은 모듈 안의 비대칭은 가장 빠른 감사 신호

본 사례의 HIGH 1건은 `grep '@Scheduled' backend/butterfly-reporting/src/main/java/**/*.java` 1회로 발견 가능했다. 같은 모듈 안 두 스케줄러가 다른 패턴을 쓰는 경우 → 거의 항상 한쪽이 회귀.

**자동화 후보**: ArchUnit 룰로 `@Scheduled` annotation 에 `zone` 속성 필수 명시 — 본 카드 회귀를 자동 가드.

## 다음에 같은 상황이면

1. 감사 의뢰 시 에이전트에게 **read-only mode 명시** + 결과는 **위험 등급 리포트 형식만 허용**
2. 감사 결과 검토 후, **사용자가 직접** 어떤 HIGH 를 어떤 패치로 잠글지 결정
3. 패치는 **새 세션** 에서, 명시적 스코프(파일 N개 이내 / 새 빈 금지 / 라인 M 이하) 와 함께 별도 에이전트에 위임
4. 적용 후 즉시 `git diff` 로 스코프 준수 확인 → 초과 시 즉시 롤백
5. 검증 (단위/통합 테스트) 그린 확인 후 commit

## References

- 본 노트가 추상화한 카드: [`../2026-05-12_aggregation-audit-cron-zone.md`](../2026-05-12_aggregation-audit-cron-zone.md)
- 패치 커밋: `d476c6b` (브랜치 `Fix/S14P31B101-191_DailyRollupCronZone`)
- 부모 SHA: `049b716`
- 관련 가이드: `backend/CLAUDE.md` §6 (타임존 정책)
