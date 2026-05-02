---
status: accepted
date: 2026-04-30
decision-makers: 역할 6 (분석/통합 검증)
consulted: 역할 1 (인증/권한), 역할 3 (이벤트), 역할 4 (캠페인), 역할 5 (발송)
related: ADR-0007, ADR-0008, ADR-0009, ADR-0010
jira: S14P31B101-121
---

# ADR-0013: Flyway 도입 — DDL 진실의 원천 + 개발 순서 번호 컨벤션

## Context and Problem Statement

현재 모든 모듈이 `spring.jpa.hibernate.ddl-auto=update`로 부팅 시 JPA가 엔티티를 보고 자동 ALTER하는 모드다. 이는 다음 한계를 가진다:

1. **표현력 부족** — 트리거, 복합 인덱스 옵션, ENUM, 권한 분리, 파티셔닝 등을 JPA 어노테이션으로 표현 불가
2. **변경 추적 불가** — 누가 언제 어떤 컬럼을 추가했는지 git에 남지 않음. PR 리뷰 단계에서 DDL 변경을 검토할 수 없음
3. **DROP/RENAME 누락** — JPA `update` 모드는 DROP을 절대 안 함. 컬럼 이름 바꾸면 새 컬럼 ADD + 옛 컬럼 잔존 (silent garbage)
4. **prod 위험성** — 어떤 ALTER가 실행될지 git에서 안 보임. 부팅 시간에 깜짝 ALTER로 잠금/마이그레이션 부담
5. **환경 간 drift** — 개발자별 로컬 DB가 각자 다른 스키마로 누적

S14P31B101-121은 reporting(구 analytics) 모듈에 다음을 요구한다:
- `audit_logs`의 append-only 트리거 2종
- DB 권한 분리
- SCHEMA_DESIGN.md와 정확히 일치하는 인덱스
- CI 파이프라인에서 마이그레이션 자동 검증

이는 `ddl-auto`로는 표현 불가능하므로 Flyway 도입이 **선택이 아닌 요구사항**이다.

ADR-0008/0009는 이미 본 ADR을 전제로 "DB 트리거와 GRANT는 Flyway(ADR-0013)를 통해 적용된다"고 명시하고 있으며, [`reporting/audit/entity/AuditLog.java`](../../butterfly-reporting/src/main/java/com/b101/butterfly/reporting/audit/entity/AuditLog.java) 클래스 docstring도 본 ADR 번호를 참조한다.

## Decision Drivers

- reporting 모듈의 트리거/권한 분리 요구는 SQL이 필수
- 단, 다른 모듈은 진행 중인 작업이 많아 일괄 전환은 위험
- 번호 컨벤션은 한 번 정하면 되돌리기 어려움 (적용된 마이그레이션은 번호 변경 불가)
- 팀 6명이 도메인을 분리해 작업하므로 같은 번호 충돌 가능성은 낮음
- Flyway 표준 패턴(번호 순서 = 적용 순서 = 의존성 순서)을 가능한 따른다

## Considered Options

### A. 역할별 번호 블록 (V100/V200/.../V600)

각 역할이 100칸 블록을 할당받음.

- 👍 팀 간 번호 충돌 0%
- 👎 의존하는 테이블이 더 큰 번호일 때 FK 순서 어긋남 — FK 후분리 패턴 필수
- 👎 처음 보는 사람에게 직관적이지 않음

### B. 개발 순서 (V1, V2, V3, ...) — 채택

- 👍 Flyway 표준 패턴, 번호 = 적용 순서 = 의존성 순서
- 👍 새 사람이 봐도 직관적
- 👎 동시 PR이 같은 번호를 만들 수 있음 → 머지 직전 점검으로 해결, Flyway가 부팅 시 즉시 감지
- 👎 충돌 시 늦은 머지 PR이 rebase

### C. 도메인별 다른 schema_history 테이블 (모듈별 분리)

`flyway.schema-history-table=flyway_schema_history_{module}`로 모듈별 이력 분리.

- 👍 모듈 간 완전 분리
- 👎 한 DB에 11종 schema_history → 운영 복잡도 증가
- 👎 모듈 간 의존(FK)이 있으면 결국 적용 순서를 사람이 조율해야 함

## Decision Outcome

**선택: Option B (개발 순서) + 점진적 전환**.

### 1. 도입 범위

- 본 PR (`S14P31B101-121`): **reporting 모듈만 Flyway 마이그레이션 도입**. V1~V12
- `butterfly-api`, `butterfly-worker`에 Flyway 의존성 추가 + 활성화
- **다른 모듈은 ddl-auto=update 유지** — reporting 모듈만 Flyway가 만들고, 다른 모듈은 JPA가 만든다 (하이브리드)

### 2. 점진적 전환 — Flyway baseline 활용

```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.locations=classpath:db/migration
```

`baseline-on-migrate=true`로 기존 DB(이미 ddl-auto로 만든 테이블이 있는 경우) 위에서도 Flyway가 깨끗이 시작한다. V1~V12는 reporting 도메인 테이블만 만들기 때문에 다른 모듈 테이블과 충돌 없음.

JPA `ddl-auto=update`는 그대로 두고, Flyway가 만든 reporting 테이블에 대해서는 JPA가 ALTER를 시도하지 않도록 **reporting 모듈 엔티티는 Flyway가 만든 스키마와 정확히 일치**해야 한다. 이는 코드 리뷰로 강제.

### 3. 번호 컨벤션

- **개발 순서대로** V1, V2, V3, ... (Flyway 기본)
- 한 PR이 여러 V*.sql을 추가할 수 있음. 그 PR 안에서는 번호가 연속이어야 함
- **PR 머지 직전 점검**: develop의 최신 V 번호 + 1부터 시작하는지 확인. 충돌 시 늦은 머지 PR이 rebase
- Flyway가 부팅 시 같은 번호 두 개 발견하면 `Found more than one migration with version N` 에러로 차단 → CI에서 즉시 발견

### 4. 마이그레이션 파일 위치

```
butterfly-reporting/src/main/resources/db/migration/V1__*.sql
butterfly-{도메인}/src/main/resources/db/migration/V*.sql   # 각 도메인 모듈 자기 자리
```

`butterfly-api`/`butterfly-worker`가 도메인 모듈을 의존하므로 클래스패스에서 모든 `db/migration/*` SQL이 자동 통합된다. Flyway 기본 위치 `classpath:db/migration`이 이를 그대로 픽업.

### 5. FK 후분리 패턴

reporting 모듈은 외부 팀 테이블(projects/campaigns/users/devices/members/message_templates/campaign_executions)을 FK로 참조한다. 외부 팀 마이그레이션이 도착하기 전에는 본 모듈만 단독 적용 가능해야 하므로:

- V1~V11: 테이블 + 트리거 + 인덱스만 (FK 제외)
- V12: reporting 모듈의 FK를 한 번에 추가 (`add_reporting_foreign_keys.sql`)
- V12는 외부 팀 테이블이 존재해야 안전하게 적용 가능. 다만 reporting 모듈 테이블 자체는 V1~V11만으로 동작 가능
  - 다른 모듈이 ddl-auto=update로 만든 테이블을 V12 FK가 참조해도 동작한다 (테이블이 존재만 하면 됨)

### 6. ddl-auto 전환 일정

본 PR에서는 reporting 모듈만 Flyway 도입. 다른 모듈로의 확산은:

1. 다른 도메인 담당자가 자기 모듈에 V*.sql 추가 (각자 PR)
2. 분기점: 모든 모듈이 Flyway로 전환된 시점에 `ddl-auto=update → validate`로 일괄 변경
3. 그 시점에 ADR 보강

### 7. 운영 규칙

- 마이그레이션 PR은 develop의 최신 V 번호 + 1로 잡는다
- 한 번 develop에 머지된 V*.sql은 **수정 금지** (내용 바꿔야 하면 새 V로 add migration)
- 파일명: `V{n}__{snake_case_description}.sql` (description에 더블 언더스코어 `__` 사용)
- DROP/RENAME처럼 **되돌리기 어려운 마이그레이션은 PR 본문에 명시** + 리뷰어 1명 추가 지정
- 한 PR이 여러 V*.sql을 만들 때 그 PR 내부에서는 번호 연속 필수

### 8. 기존 audit_logs 처리 (reporting 모듈 한정)

reporting 모듈의 [`AuditLog`](../../butterfly-reporting/src/main/java/com/b101/butterfly/reporting/audit/entity/AuditLog.java) 엔티티는 이미 develop에 합류해 있으며, ddl-auto가 만든 audit_logs 테이블이 dev DB에 존재할 수 있다. 본 PR의 V3 audit_logs DDL은 엔티티의 `@Column` 매핑과 정확히 일치하도록 작성한다:

- `audit_id` (BIGINT, PK, AUTO_INCREMENT)
- `project_id` (BIGINT, NULL)
- `member_id` (BIGINT, NOT NULL)
- `action` (VARCHAR(100))
- `target_type` (VARCHAR(50))
- `target_id` (BIGINT, NULL)
- `detail` (JSON, NULL)
- `ip_address` (VARCHAR(45))
- `user_agent` (VARCHAR(500))
- `created_at` (DATETIME, DEFAULT CURRENT_TIMESTAMP)

기존 dev DB는 본 마이그레이션이 처음 적용될 때 baseline-on-migrate로 V0 baseline 후 V1~ 적용을 시도하지만, **이미 같은 이름의 테이블이 있으면 `CREATE TABLE`이 실패한다.** 이를 위해 본 PR의 V*.sql들은 dev/prod에서 **빈 DB 또는 reporting 테이블이 아직 없는 상태**에서 처음 적용되는 것을 전제로 한다. 운영 데이터가 들어가 있는 dev DB에 적용해야 하는 경우, 별도 절차(`flyway baseline --baseline-version=12` 또는 ddl-auto 산출물 DROP 후 마이그레이션 적용)로 다룬다.

## Consequences

### 좋은 점

- reporting 모듈의 트리거/권한 분리/복합 인덱스가 SQL로 정확히 표현됨
- DDL 변경이 PR에 명시적으로 노출 → 리뷰 가능
- 환경 간 drift 0 (Flyway가 만든 테이블 한정)
- prod 적용 시 어떤 ALTER가 돌지 사전에 명확

### 나쁜 점 / 주의

- **하이브리드 모드 위험**: reporting 엔티티가 Flyway 스키마와 어긋나면 JPA가 ALTER를 시도할 수 있음 → reporting 엔티티 수정 시 반드시 V*.sql 동반
- **번호 충돌 마찰**: 동시 작업 PR이 같은 V를 만들면 늦은 쪽이 rebase. 머지 빈도가 낮은 현재 팀 규모에서는 영향 작음
- **외부 팀 의존 FK 적용 지연**: V12(FK)는 외부 팀 테이블이 도착해야 안전. reporting 모듈만 보면 V1~V11으로 충분
- **기존 dev DB 처리 절차 필요**: ddl-auto가 만든 reporting 테이블이 있으면 `CREATE TABLE` 충돌 — §8 참고

### Post-MVP

- 모든 모듈로 Flyway 확산 완료 시 `ddl-auto=update → validate` 일괄 전환 + ADR 보강
- 마이그레이션 100개 이상 누적 시 baseline으로 압축
- prod 적용 전 dry-run을 위한 `flyway info`/`flyway validate` CI 단계 추가

## More Information

- ADR-0007 — 분석 집계 테이블 v2 (V7~V11이 다루는 테이블의 결정 근거)
- ADR-0008/0009 — 감사 로그 (V3/V4가 다루는 테이블/트리거의 결정 근거)
- ADR-0010 — 저장 전략·반정규화 기준 (스냅샷 컬럼 등)
- 단일 진실 원천: [`butterfly-reporting/db/schema_analytics_audit.sql`](../../butterfly-reporting/db/schema_analytics_audit.sql) v2.0
- [`butterfly-reporting/docs/SCHEMA_DESIGN.md`](../../butterfly-reporting/docs/SCHEMA_DESIGN.md)
- 관련 Jira: `S14P31B101-121` (본 ADR), `S14P31B101-125` (DB 권한 분리, V13+)
