---
status: accepted
date: 2026-04-29
decision-makers: 역할 6 (분석/감사 모듈)
consulted: 역할 1 (인증/테넌트/프로젝트/권한), 전 도메인 (감사 이벤트 발행자)
supersedes: ADR-0008
---

# ADR-0009: 감사 로그 소유 모듈 이전 — `butterfly-tenant` → `butterfly-reporting`

## Context and Problem Statement

[ADR-0004](0004-audit-log-architecture.md) → [ADR-0008](0008-audit-log-architecture-v2.md)을 거치면서 `audit_logs` 테이블의 스키마·append-only 보장·CSV 내보내기 등은 안정화되었다. 그러나 모듈러 모놀리스 전환 합의(2026-04-29, Notion "모듈러 모놀리스 전환 합의")에서 **AuditLog의 소유 모듈을 재검토**하기로 결정했다.

현재(ADR-0008 직후) 상태:

- AuditLog 엔티티/레포는 `butterfly-tenant`에 위치 ([S14P31B101-129](https://ssafy.atlassian.net/browse/S14P31B101-129))
- `ApplicationService`(tenant)에서 직접 `auditLogRepository.save(...)` 호출
- 다른 도메인(campaign, segment, delivery 등)이 감사 액션을 남기려면 tenant 모듈에 의존해야 함

문제:

1. **횡단 관심사가 한 도메인에 묶여 있음** — AuditLog는 tenant만의 자원 변경 이력이 아니라 **모든 도메인의 감사 이력**이다. tenant 모듈이 모든 도메인의 audit 기록 책임을 떠안는 구조는 [ADR-0005](0005-auth-filter-placement.md)의 "tenant 단방향 의존" 원칙을 흔든다.
2. **모듈러 모놀리스 경계와 충돌** — campaign·segment·delivery가 audit를 남기려면 tenant.repository.AuditLogRepository를 직접 호출하거나, tenant 서비스를 거쳐야 한다. ArchUnit ModularBoundaryTest의 "campaign이 tenant.repository를 import 못한다" 규칙과 정면 충돌 가능.
3. **read model의 자연스러운 위치** — `audit_logs`는 본질적으로 분석·감사용 read-only 데이터(append-only). reporting 모듈의 5종 집계 테이블(ADR-0007)과 같은 카테고리.

## Decision Drivers

- 모듈러 모놀리스 경계: **각 도메인은 자기 자원만 소유, 횡단 read model은 reporting에 모은다**
- 발행자(write)와 저장자(store)의 분리 — 어떤 도메인이든 audit를 남기되, 저장은 한 곳
- 기존 v2 스키마(컬럼·트리거·인덱스)는 변경 없음 — 위치만 이전
- 운영 무중단 — 1 sprint 그레이스 기간으로 이중 쓰기 모니터링 후 이전 완료

## Considered Options

### 1. AuditLog 위치

| 위치 | 장점 | 단점 |
|------|------|------|
| `butterfly-tenant` (현재) | 이미 구현됨, 신청서·프로젝트 audit는 tenant가 발행자 | 횡단 관심사가 한 도메인에 종속, 다른 도메인에서 사용 시 의존 방향 위반 |
| **`butterfly-reporting`** | read model의 자연스러운 위치, 모든 도메인이 동등하게 발행자 | 코드 이전 필요, 1 sprint 그레이스 기간 동안 이중 쓰기 |
| `butterfly-audit` 신설 | 관심사 완전 분리 | MVP 단계 모듈 수 증가, ADR-0005 "MVP 과설계 회피" 원칙 위반 |

### 2. 발행 방식

| 방식 | 장점 | 단점 |
|------|------|------|
| 직접 `AuditLogRepository.save()` 호출 | 단순 | 모든 발행 모듈이 reporting의 repository에 의존 → 모듈 경계 위반 |
| **도메인 이벤트** (`AuditRecordedEvent` via `ApplicationEventPublisher`) | 발행자가 reporting에 의존하지 않음, 트랜잭션 경계 명확 (`@TransactionalEventListener(AFTER_COMMIT)`) | 디버깅 시 호출 추적 한 단계 추가 |
| Outbox 패턴 | 메시지 유실 방지 | 운영 복잡도 ↑, MVP 범위 초과 |

## Decision Outcome

### 1. 위치 — `butterfly-reporting`

AuditLog 엔티티·레포지토리·관련 서비스를 `butterfly-tenant`에서 `butterfly-reporting`으로 이전한다.

스키마 변경 없음:

- 테이블명 `audit_logs` 유지
- 컬럼·인덱스·append-only 트리거(ADR-0008) 모두 유지
- 데이터 마이그레이션은 운영 환경에서 `INSERT INTO reporting.audit_logs SELECT * FROM tenant.audit_logs`

### 2. 발행 — 도메인 이벤트

```java
// 발행자 (tenant, campaign, segment 등 모든 도메인)
eventPublisher.publishEvent(new AuditRecordedEvent(
    actorMemberId, action, targetType, targetId, projectId, ipAddress, userAgent, detail
));

// 구독자 (butterfly-reporting)
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onAuditRecorded(AuditRecordedEvent e) {
    auditLogRepository.save(e.toEntity());
}
```

`AuditRecordedEvent` record는 `butterfly-common/.../global/event/`에 위치 — 모든 모듈이 발행 가능.

### 3. 이전 절차 (3 PR로 분할)

| 단계 | 내용 | PoNR |
|---|---|---|
| **P7-1** | reporting에 AuditLog 엔티티/레포 신설 + `AuditEventListener` 구독자 추가. tenant·reporting 양쪽에 동일 테이블이 생기지만 reporting 쪽은 비어있음 | ❌ |
| **P7-2** | tenant `ApplicationService`의 직접 호출을 `eventPublisher.publishEvent(...)`로 전환. listener가 reporting 테이블에 기록. **동시에 tenant `audit_logs`에는 더 이상 쓰지 않음** | ❌ (롤백 = 직접 호출 복구) |
| **P7-3** | 1 sprint 그레이스 모니터링 후, 마이그레이션 SQL로 tenant.audit_logs를 reporting.audit_logs에 흡수 → tenant 테이블 DROP | ✅ **PoNR** (직전 release tag 필수) |

각 단계는 독립 Story로 발행 (별도 백로그).

## Consequences

### 좋은 점

- 모듈 경계 명확: 모든 도메인이 reporting에 audit 이벤트 발행, reporting은 audit 저장 단독 책임
- ArchUnit `ModularBoundaryTest`와 정합 — campaign/segment/delivery가 더 이상 tenant.repository를 거칠 필요 없음
- `audit_logs` + 5종 집계 테이블(ADR-0007)이 한 모듈에 모여 read-side 응집도 ↑
- 향후 마이크로서비스 전환 시 reporting 단독 분리 용이

### 나쁜 점 / 주의

- **1 sprint 이중 쓰기 기간** — 모니터링 메트릭(`audit.dual_write.diff_count`) 필요. 기간 내 tenant→reporting 이동 누락 시 데이터 손실 가능 → P7-3 직전에 row count 일치 확인
- **이벤트 트랜잭션 경계** — `@TransactionalEventListener(AFTER_COMMIT)` 사용. 발행자 트랜잭션이 롤백되면 audit도 기록되지 않음(이게 정상 동작). 테스트에서 이 동작 검증 필수
- **호출 추적 복잡도** — IDE에서 "이 audit가 어디서 발행됐는지" 검색 시 직접 호출이 아니라 이벤트 → 리스너로 한 단계 늘어남. `AuditRecordedEvent` 사용처를 grep하면 모든 발행 지점 확인 가능 (단일 record라 추적 용이)
- **ADR-0008 결정 번복 비용** — 머지된 결정을 1 sprint 만에 뒤집는다. 신뢰 비용을 줄이기 위해 합의 회의 시 v2 스키마는 그대로라는 점, 모듈 경계 강제(ArchUnit 도입)가 새로 들어왔다는 점을 강조

## 후속 작업

- **P7 시리즈** — 실제 코드 이전 (별도 Story, 본 ADR 머지 후 착수)
- `butterfly-reporting` 모듈 개명 (P3, customer/delivery와 함께 일괄)
- `butterfly-archtest`의 ArchRule에 "tenant 외 모듈이 `..tenant.entity.AuditLog`를 import 못한다" 규칙 추가 (P7-3 후)

## 관련 문서

- 모듈러 모놀리스 전환 합의: Notion "모듈러 모놀리스 전환 합의" PART 2 §2.8
- 선행 ADR: [ADR-0004](0004-audit-log-architecture.md) (v1, superseded), [ADR-0008](0008-audit-log-architecture-v2.md) (v2 스키마, superseded by 본 ADR)
- 관련 Jira: [S14P31B101-130](https://ssafy.atlassian.net/browse/S14P31B101-130) (Epic), [S14P31B101-136](https://ssafy.atlassian.net/browse/S14P31B101-136) (본 ADR Story)
