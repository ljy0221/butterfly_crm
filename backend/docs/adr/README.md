<!-- markdownlint-disable MD040 MD060 -->

# Architecture Decision Records (ADR)

butterfly 프로젝트의 주요 아키텍처 결정을 기록한다.

## 왜 ADR인가

- Git 커밋 메시지는 "무엇을 바꿨는지"만 남기지, "왜 그 구조를 택했는지"와 "어떤 대안을 기각했는지"는 사라진다.
- ERD/API 문서는 **현재 상태**만 보여주지, 결정의 맥락은 담지 않는다.
- 다음 분기에 "왜 캠페인이 정의/실행으로 분리되어 있지?"를 누군가 물을 때 이 문서가 답이 된다.

## 양식

[MADR (Markdown Any Decision Records)](https://adr.github.io/madr/) 4.0 의 간소화된 형태를 쓴다.

```
---
status: proposed | accepted | rejected | deprecated | superseded by ADR-XXXX
date: YYYY-MM-DD
decision-makers: 역할 N, 역할 M
consulted: ...
---

# ADR-XXXX: 제목

## Context and Problem Statement
## Decision Drivers
## Considered Options
## Decision Outcome
### Consequences
## More Information
```

## 작성 기준

다음 중 하나라도 해당되면 ADR을 남긴다:

- 여러 역할/도메인이 함께 영향을 받는 스키마·API 구조 결정
- 대안을 여럿 검토한 끝에 내린 결정 (기각된 대안의 이유가 미래 가치 있음)
- 되돌리려면 여러 모듈을 고쳐야 하는 결정
- 요구사항 문서만 봐서는 추론하기 어려운 결정

"상수 값 변경" 같은 자잘한 건 커밋 메시지로 족하다.

## 번호 규칙

- 4자리 0-padding (`ADR-0001`, `ADR-0002`, ...)
- 한 번 부여된 번호는 재사용하지 않는다 (기각돼도 `status: rejected`로 남긴다)

## 색인

| # | Status | Title | Date |
|---|--------|-------|------|
| [ADR-0001](0001-segment-condition-tree-json.md) | superseded by ADR-0006 | 세그먼트 조건 트리를 JSON 컬럼으로 저장 | 2026-04-21 |
| [ADR-0002](0002-campaign-definition-execution-split.md) | accepted | 캠페인 정의와 실행 회차 분리 | 2026-04-21 |
| [ADR-0003](0003-analytics-aggregation-tables.md) | superseded by ADR-0007 | 분석용 일자별 집계 테이블 도입 (Blocker 1) | 2026-04-21 |
| [ADR-0004](0004-audit-log-architecture.md) | superseded by ADR-0008 | 감사 로그 아키텍처 (테이블·서비스·조회 API) | 2026-04-21 |
| [ADR-0005](0005-auth-filter-placement.md) | accepted | 인증/인가 필터 배치 및 `@PreAuthorize` 통일 | 2026-04-21 |
| [ADR-0006](0006-segment-condition-spec-v2.md) | accepted | 세그먼트 조건 트리 스펙 v2 — 요구명세 개정 반영 | 2026-04-27 |
| [ADR-0007](0007-analytics-aggregation-tables-v2.md) | accepted | 분석 집계 테이블 v2 — 회차·템플릿·실패·전사 집계 분리 | 2026-04-28 |
| [ADR-0008](0008-audit-log-architecture-v2.md) | superseded by ADR-0009 | 감사 로그 v2 — append-only 3중 방어 + IP/UA 기록 | 2026-04-28 |
| [ADR-0009](0009-audit-log-relocation-to-reporting.md) | accepted | 감사 로그 소유 모듈 이전 — tenant → reporting | 2026-04-29 |
| [ADR-0010](0010-storage-strategy-and-denormalization.md) | accepted | 저장 전략·반정규화 기준 — 도메인 횡단 가이드 | 2026-04-29 |
