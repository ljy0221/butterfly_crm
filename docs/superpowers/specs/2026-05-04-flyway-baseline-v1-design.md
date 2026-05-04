# Flyway 베이스라인 통합 설계 (V1/V2/V3 분할)

> 작성일: 2026-05-04
> 작성자: 남정연
> 관련 ADR: ADR-0013 (마이그레이션 정책)
> 검토: 제안자/비판자 회의(2026-05-04) 결과 반영

## 1. 배경

현재 Flyway 마이그레이션은 `butterfly-reporting` 모듈에만 V1~V13으로 존재하고, reporting 도메인 테이블만 다룬다. 다른 도메인(`projects`, `users`, `members`, `campaigns`, `segments`, `events` 등)은 JPA `ddl-auto=update`가 만든다 — 즉 스키마가 두 가지 경로로 생성되는 하이브리드 상태다.

이번 작업은 첨부된 ERD(`butterfly-erd.sql`)를 베이스라인으로 통합해 모든 테이블을 Flyway로 관리한다. 운영 데이터가 없어 DB 초기화가 가능한 시점에 한 번에 정리한다.

## 2. 목표

- 첨부 ERD를 정제해 베이스라인 V1/V2/V3을 만든다 (3분할).
- 기존 reporting V1~V13의 정의는 V2/V3에 흡수한다 (정의 충돌 시 reporting 우선).
- 마이그레이션 위치를 `butterfly-api`로 이전한다.
- reporting 모듈의 `db/migration/` 디렉토리와 원본 SQL을 삭제한다.
- 도메인별 JPA 엔티티 ↔ 베이스라인 정합성 검증은 도메인별 후속 티켓으로 분리한다.
- 모든 도메인 정합성 검증이 끝난 뒤 별도 티켓에서 `ddl-auto=update` → `validate` 전환한다 (일몰 조건).

## 3. 비목표 (이번 작업 범위 밖)

- JPA 엔티티 ↔ 베이스라인 스키마의 정합성 검증·보정 (도메인별 후속 티켓)
- ADR-0008 §append-only 3중 방어선의 DB 권한 분리 (S14P31B101-125, 별도)
- 첨부 ERD의 깨진 한글 COMMENT 복원 (이번에는 모두 제거)
- `members.role_type`의 `EDITOR`/`VIEWER` 등 ADR-0005 인가 정책과의 정합성 점검
- `butterfly-migration` 같은 신규 모듈 신설 (보수적으로, 신설하지 않음)
- `ddl-auto=validate` 전환 (모든 도메인 정합성 검증 완료 후 별도 티켓)
- B그룹 테이블의 일반 인덱스 보강 (정합성 티켓에서 처리, 단 명백한 복합 UK는 V1에서 잡음)

## 4. 결과물

### 4.1 디렉토리 구조 변화

```
backend/butterfly-api/src/main/resources/db/migration/
  V1__core_baseline.sql           (신규 — B그룹: 첨부 ERD 정제)
  V2__reporting_baseline.sql      (신규 — A그룹: reporting V1~V11+V13 흡수)
  V3__audit_triggers.sql          (신규 — V4의 audit_logs 트리거)

backend/butterfly-reporting/src/main/resources/db/migration/   ← 디렉토리 삭제
backend/butterfly-reporting/db/schema_analytics_audit.sql      ← 삭제
```

**3분할 이유** (회의 결과):
- MySQL DDL은 암묵 커밋이라 단일 V에서 부분 실패 시 좀비 상태 발생. V1 통과 후 V2에서 깨지면 V1 흔적은 살리고 V2만 재작성 가능.
- 코드 리뷰 단위가 도메인 경계와 정렬됨 (B그룹/A그룹/트리거).
- FK는 ADR-0013 §5 정책대로 후속 V로 분리.

### 4.2 V1 (`V1__core_baseline.sql`) — 첨부 ERD 정제 (B그룹, 22개 테이블)

`projects`, `members`, `applications`, `users`, `devices`, `campaigns`, `campaign_executions`, `message_templates`, `segments`, `segment_members`, `segment_favorites`, `segment_snapshots`, `segment_snapshot_users`, `events`, `event_categories`, `event_schemas`, `event_schema_fields`, `event_daily_agg`, `user_tags`, `tag_definitions`, `token_blacklist`, `refresh_tokens`

**제외**: `Eng(Key)` (용도 불명).

### 4.3 V2 (`V2__reporting_baseline.sql`) — reporting 흡수 (A그룹, 10개 테이블)

`notification_logs` (V1 + V13의 `message_key` 컬럼/UK 포함), `user_action_logs`, `audit_logs`, `send_reconciliations`, `system_health_snapshots`, `campaign_stats_hourly`, `campaign_stats_daily`, `template_stats_daily`, `failure_stats_daily`, `project_stats_daily`

기존 reporting V1~V11 정의를 그대로 흡수하고, V13의 `notification_logs.message_key` 컬럼·UK를 합친다.

### 4.4 V3 (`V3__audit_triggers.sql`) — audit_logs 트리거

기존 reporting V4의 `trg_audit_logs_no_update`, `trg_audit_logs_no_delete` 트리거를 그대로 옮긴다.

### 4.5 첨부 ERD 정제 규칙 (V1 작성 시 적용)

1. **PRIMARY KEY 명시 추가** — 첨부 ERD의 모든 테이블에 PK 선언이 없다. 컬럼명·`AUTO_INCREMENT` 표시·도메인 엔티티(`@Id`/`@IdClass`/`@Embeddable`)를 함께 봐서 결정한다.
   - 단일 PK 후보: `application_id`, `campaign_id`, `campaign_execution_id`, `category_id`, `device_id`, `event_id`, `field_id`, `member_id`, `message_template_id`, `project_id`, `schema_id`, `segment_id`, `tag_def_id`, `token_id`, `user_id`, `user_tag_id`, `blacklist_id`
   - 복합 PK 후보: `event_daily_agg(log_date, project_id, category_id, user_id)`, `segment_favorites(segment_id, member_id)`, `segment_members(segment_id, user_id)`, `segment_snapshot_users(snapshot_id, user_id)`, `segment_snapshots(snapshot_id)` (단독)
   - 구현 단계 첫 작업: 도메인 엔티티 빠르게 훑어보고 PK가 엔티티와 어긋나는 경우 표로 정리해 V1에 반영.
2. **잘못된 `AUTO_INCREMENT` 제거** — FK 컬럼에 붙은 `AUTO_INCREMENT` 제거 (`campaigns.message_template_id`, `campaign_executions.campaign_id`).
3. **컬럼명 오타 수정**
   - `audit_logs.member_id2` → `member_id` (단, V2의 reporting 흡수 정의가 이미 `member_id`이므로 자동 해소)
   - `campaigns.send_mode ENUM(\`INNEDIATE\`, \`SCHEDULED\`, 'RECURRING')` → `ENUM('IMMEDIATE','SCHEDULED','RECURRING')`
4. **문법 오류 수정**
   - `repeat_time\`` → `repeat_time`
   - `project_stats_daily.stat_id` 트레일링 `\` 제거 (V2 흡수 정의로 자동 해소)
   - `BIGINT NOT NULL DEFAULT AUTO_INCREMENT` → `BIGINT NOT NULL AUTO_INCREMENT` (PK 컬럼만)
5. **의문 컬럼 제거**
   - `notification_logs.Field BIGINT NULL` 삭제 (V2 흡수 정의로 자동 해소)
6. **COMMENT 처리**
   - 깨진 mojibake COMMENT는 모두 제거 (V1).
   - V2/V3는 reporting의 정상 한글 COMMENT 그대로 유지.
7. **테이블 옵션** — 모든 테이블에 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` 적용.
8. **인덱스 (V1)**
   - PK + 명백히 데이터 정합성을 보호하는 복합 UK까지만 잡는다.
   - 명백한 복합 UK 후보 (작성 시 엔티티 확인 후 확정):
     - `user_tags(project_id, user_id, tag_key)` — 같은 프로젝트의 유저 + 키 조합 중복 금지
     - `event_categories(project_id, name)` — 프로젝트 내 이벤트명 유일
     - `tag_definitions(project_id, tag_key)` — 프로젝트 내 태그 키 유일
     - `event_schemas(project_id, category_id, version)` — 같은 카테고리의 같은 버전 중복 금지
     - `event_schema_fields(schema_id, field_name)` — 같은 스키마 내 필드명 중복 금지
   - 위 외의 일반 조회 인덱스는 정합성 검증 티켓에서 보강.

### 4.6 V1/V2/V3에서 외래키(FK)는 추가하지 않는다

- ADR-0013 §5 정책 유지. FK는 후속 V (V4~)에서 도메인별 또는 일괄로 추가.
- 베이스라인은 테이블 + PK + 명백한 UK + 트리거까지만 다룬다.

## 5. 구현 단계

1. `backend/butterfly-api/src/main/resources/db/migration/` 디렉토리 생성.
2. **V1 작성 사전 작업**: 도메인 엔티티 빠르게 확인 (특히 §4.5의 PK·UK 후보가 엔티티와 일치하는지). 차이는 표로 기록하고 V1에 반영.
3. `V1__core_baseline.sql` 작성 (§4.2, §4.5 적용).
4. `V2__reporting_baseline.sql` 작성 (§4.3 — 기존 reporting V1+V13/V2/V3/V5/V6/V7/V8/V9/V10/V11 통합).
5. `V3__audit_triggers.sql` 작성 (§4.4 — V4 트리거 그대로).
6. reporting 모듈 마이그레이션 디렉토리 삭제: `backend/butterfly-reporting/src/main/resources/db/migration/` 전체.
7. reporting 원본 SQL 삭제: `backend/butterfly-reporting/db/schema_analytics_audit.sql`.
8. `butterfly-api/build.gradle`의 Flyway 의존성 주석 갱신.
9. `butterfly-api/application.properties`의 Flyway 주석 갱신 + `ddl-auto=update` 일몰 조건 명시.
10. `butterfly-worker/application.properties`에서 `spring.flyway.enabled=false`로 변경. 주석으로 "api healthcheck 통과 후 부팅 보장 필요" 명시.
11. **로컬 검증**: `DROP DATABASE butterfly; CREATE DATABASE butterfly...` → `./gradlew :butterfly-api:bootRun` → V1/V2/V3 적용 성공 확인 + Hibernate validation 통과 여부 확인.
12. **testcontainer 검증**: `butterfly-testsupport` 또는 신규 통합 테스트에서 MySQL 8 testcontainer를 띄우고 V1/V2/V3 단독 적용 + Hibernate `ddl-auto=update`가 만드는 추가 ALTER 로그 캡처. 차이점은 도메인별 후속 티켓 백로그에 기록.

## 6. 설정 변경 요약

### 6.1 `butterfly-api/application.properties`

```properties
# Flyway (ADR-0013) — butterfly-api가 베이스라인 V1/V2/V3을 보유.
# 모든 도메인 테이블이 V1(core)/V2(reporting)/V3(triggers)으로 생성된다.
# ddl-auto=update는 베이스라인 안정화까지의 한시 정책. 도메인별 정합성 검증이
# 모두 끝나면 별도 티켓에서 ddl-auto=validate로 강등한다.
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.validate-on-migrate=true
spring.jpa.hibernate.ddl-auto=update
```

### 6.2 `butterfly-worker/application.properties` — Flyway 비활성화 (회의 결정 Q3-a)

worker는 마이그레이션 주체가 아니다. api가 먼저 부팅해 V1/V2/V3을 적용한 후 worker가 부팅하도록 운영 순서를 보장한다.

```properties
# Flyway 비활성화 — butterfly-api가 마이그레이션 주체.
# docker-compose의 depends_on: condition: service_healthy + api healthcheck로
# api 마이그레이션 완료 후 worker 부팅을 보장해야 한다.
# (api의 healthcheck가 Flyway 적용 완료 후 200을 반환하도록 별도 티켓에서 보강)
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=update
```

### 6.3 `butterfly-api/build.gradle`

기존 Flyway 의존성 그대로. 주석 갱신만:

```gradle
// Flyway (ADR-0013) — butterfly-api가 베이스라인 V1/V2/V3을 보유한다.
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-mysql'
```

### 6.4 `butterfly-reporting/build.gradle`

Flyway 의존성을 직접 선언하지 않으므로 변경 없음. 마이그레이션 파일만 빠진다.

## 7. 검증 시나리오

| # | 시나리오 | 기대 결과 |
|---|---------|----------|
| 1 | 빈 DB → `:butterfly-api:bootRun` | V1/V2/V3 순차 적용 성공, 32개 테이블 + 2개 트리거 생성, `flyway_schema_history` 3개 row |
| 2 | 각 테이블 `SHOW CREATE TABLE` | PK 모두 존재, ENGINE=InnoDB, charset utf8mb4, §4.5 UK 존재 |
| 3 | `audit_logs` UPDATE 시도 | SQLSTATE 45000 |
| 4 | Hibernate validation | 도메인별 차이 로그 캡처 → 후속 티켓 백로그 |
| 5 | reporting 디렉토리/원본 SQL 삭제 후 빌드 | 모든 모듈 컴파일 성공 |
| 6 | testcontainer MySQL 8에서 V1→V2→V3 단독 적용 | 3개 마이그레이션 적용 성공, 트리거 동작 확인 |
| 7 | worker 단독 부팅 (`:butterfly-worker:bootRun`) | Flyway 비활성으로 마이그레이션 시도 없음, 부팅 성공 (DB 스키마는 사전 존재 가정) |

## 8. 위험 요소

- **JPA 엔티티 ↔ 베이스라인 불일치**: `ddl-auto=update`가 컬럼을 추가/수정해 schema_history와 실제 DB가 환경마다 갈라질 수 있다. 회의 합의: 정합성 검증 후속 티켓이 도메인별로 끝나면, 모든 도메인 완료 후 별도 티켓에서 `ddl-auto=validate`로 강등해 이 위험을 봉쇄한다. 그 전까지의 위험은 §9의 후속 티켓 우선순위로 관리.
- **worker 부팅 순서 보장**: docker-compose `depends_on: condition: service_healthy`가 api healthcheck에 의존한다. api healthcheck가 Flyway 완료 후 200을 반환하는지는 현재 spec 범위 밖이라 별도 티켓에서 보강. 임시로는 `condition: service_started` + 사람 확인.
- **첨부 ERD의 의미적 정확성**: ERD가 도메인 의도와 100% 일치한다는 보장이 없다. PK·UK는 §4.5의 사전 작업에서 엔티티와 대조해 잡되, 일반 인덱스는 정합성 티켓으로 미룬다.
- **PK 추론 오류**: 복합 PK 후보(`segment_snapshot_users`, `event_daily_agg` 등)가 엔티티와 어긋날 수 있다. 구현 단계 2번에서 엔티티 확인을 통해 사전 보정.

## 9. 후속 작업 (별도 티켓)

- 도메인별 정합성 검증 (8개): tenant, auth, event, segment, campaign, notification, reporting, common
- 첨부 ERD COMMENT 한글 복원 (V4 또는 도메인별)
- FK 일괄 추가 (V5 또는 도메인별, ADR-0013 §5)
- **모든 도메인 정합성 검증 완료 후**: `spring.jpa.hibernate.ddl-auto=update` → `validate` 전환 (api/worker 동시) — 베이스라인 일몰 조건
- api healthcheck를 Flyway 완료 후 200 반환하도록 보강 (worker `service_healthy` 조건 만족용)
- ADR-0013 보강 (베이스라인 정책 명시) + ADR-0014 신규 (V1/V2/V3 분할 정책 + ddl-auto 일몰 조건)

## 10. 회의 결과 반영 요약

회의(2026-05-04)에서 다음 결정 사항을 spec에 반영:

| 결정 | 반영 위치 |
|------|----------|
| V1 단일 → V1/V2/V3 3분할 | §4.1, §4.2~§4.4 |
| `ddl-auto` 일몰 조건 명시 | §6.1, §9 |
| worker Flyway 비활성화 + healthcheck 보장 | §6.2, §9 |
| 신규 모듈(`butterfly-migration`) 신설 안 함 (보수적 결정) | §3 비목표 |
| testcontainer 검증 단계 추가 | §5-12, §7-6 |
| B그룹 명백한 복합 UK는 V1에서 잡음 | §4.5-8 |
| PK 추론을 엔티티와 대조 | §4.5-1, §5-2 |
| reporting 디렉토리 삭제 명시 (사각지대 보강) | §4.1, §5-6 |
