# Dev Journey Index

> 결과만이 아닌 **과정의 기록**. "그때 왜 그랬지?" 에 답하기 위한 자료.
> 작성 가이드는 [`README.md`](README.md).

## 시간순

| 날짜 | 카테고리 | 슬러그 | 한 줄 요약 |
|---|---|---|---|
| 2026-05-07 ~ 08 | INCIDENT | [oom-cycle](2026-05-07_oom-cycle.md) | CI integration-test OOMKilled — 1차 가설 부분 적중 + 2단계 fix 사이클 (develop 머지 완료 `0bcc85b`) |

## 카테고리별

### INCIDENT
- 2026-05-07 ~ 08 [oom-cycle](2026-05-07_oom-cycle.md)

## 막다른 길 재방문 트리거

| 트리거 조건 | 재검토 항목 | 카드 |
|---|---|---|
| Code Freeze 5/15 후 + 여유 + 깜빡임 재발 | 광범위 EmbeddedKafka 교체 | [oom-cycle](2026-05-07_oom-cycle.md) |
| OOM 사이클 종료 후 별도 PR | RabbitMQ 의존성 삭제 | [oom-cycle](2026-05-07_oom-cycle.md) |
| workers.max=2 + 컨테이너 공유로도 OOM 재발 | K8s pod memory limit 인상 vs workers.max=1 | [oom-cycle](2026-05-07_oom-cycle.md) |

## Cross-reference (Optimization Historian)

| Journey 카드 (과정) | Historian 카드 (결과) | 관계 |
|---|---|---|
| [oom-cycle](2026-05-07_oom-cycle.md) | [ci-gate-restoration](../optimizations/2026-05-07_ci-gate-restoration.md) | **선행 작업** — 게이트 복구로 OOM이 RED로 가시화됨 |
</content>
</invoke>