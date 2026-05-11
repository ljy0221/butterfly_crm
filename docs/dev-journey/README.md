# Dev Journey Index

> 결과만이 아닌 **과정의 기록**. "그때 왜 그랬지?" 에 답하기 위한 자료.
>
> 작성 가이드는 [`README.md`](README.md). 결과 카드는 [`docs/optimizations/`](../optimizations/INDEX.md).

## 카테고리

- **DEBUG** — 버그 추적
- **FEATURE** — 신규 구현
- **REFACTOR** — 개선
- **DECISION** — 의사결정 분기
- **INVESTIGATION** — 조사·탐구
- **INCIDENT** — 장애 대응

## 시간순

| 날짜 | 카테고리 | 슬러그 | 한 줄 요약 | Historian |
|---|---|---|---|---|
| 2026-05-07 ~ 08 | INCIDENT | [oom-cycle](2026-05-07_oom-cycle.md) | CI integration-test OOMKilled(exit 137) — 1차 가설(컨테이너 공유)이 부분적으로 옳았으나 충분하지 않았던 2단계 fix 사이클. 모듈 간 fork JVM 동시성을 놓쳐 재발 → workers.max 4→2 로 닫음 | (CI 게이트 복구는 [ci-gate-restoration](../optimizations/2026-05-07_ci-gate-restoration.md)) |

## 카테고리별

### INCIDENT
- 2026-05-07 ~ 08 [oom-cycle](2026-05-07_oom-cycle.md) — CI OOMKilled 트러블슈팅 2단계 사이클

### DEBUG / FEATURE / REFACTOR / DECISION / INVESTIGATION
- _(아직 카드 없음)_

## 학습 포인트 인덱스 (자주 보는 페이지)

- **컨테이너 공유의 효과 범위 오해 — JVM 내 vs 모듈 간 분리** ([oom-cycle](2026-05-07_oom-cycle.md) §Lessons #2)
- **OOM 진단의 추정 의존성 — 메모리 정점 측정 도구 부재 시 다단계 fix로 좁히기** ([oom-cycle](2026-05-07_oom-cycle.md) §Lessons #1)
- **로컬 검증의 한계 — K8s pod memory limit 컨텍스트 부재 시 재현 불가** ([oom-cycle](2026-05-07_oom-cycle.md) §Lessons #4)
- **사용자 광범위 제안의 신중한 평가 — 이미 끝난 작업 짚어 스코프 좁히기** ([oom-cycle](2026-05-07_oom-cycle.md) §Lessons #3)

## 막다른 길 인덱스 (재방문 트리거)

| 폐기 시점 | 폐기한 접근 | 재방문 트리거 | 카드 |
|---|---|---|---|
| 2026-05-07 | 광범위 EmbeddedKafka 일괄 교체 | Code Freeze 5/15 후 + 2~3일 여유 + 깜빡임 재발 시 | [oom-cycle](2026-05-07_oom-cycle.md) §Dead End 1 |
| 2026-05-07 | RabbitMQ 의존성 일괄 삭제 (이번 PR에 묶기) | OOM 사이클 종료 + 별도 PR 분리 후 | [oom-cycle](2026-05-07_oom-cycle.md) §Dead End 2 |
| 2026-05-07 | K8s pod memory limit 인상 (1차 시점) | workers.max=2 + 컨테이너 공유로도 OOM 재발 시 (workers.max=1 vs limit 인상 양자 택일 협의 필요) | [oom-cycle](2026-05-07_oom-cycle.md) §Dead End 3 |

## 관련 결과 카드 (Optimization Historian)

- 2026-05-07 [ci-gate-restoration](../optimizations/2026-05-07_ci-gate-restoration.md) — `allow_failure: true` 제거 + Flyway/Jib 회귀 fix. **본 OOM 사이클의 직전 작업**: CI 그린 게이트가 정상화되었기에 OOM이 RED로 가시화될 수 있었음.
</content>
</invoke>