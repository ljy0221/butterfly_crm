# [Proposal] audit_logs DB 권한 분리 운영 적용

> Owner: 이재영
> 최초 작성: 2026-05-04
> 상태: **합의 대기 (proposed)**
> 관련 Jira: `S14P31B101-173` (제안서/스크립트 — 173-A, 본 PR), `S14P31B101-183` (운영 적용 — 173-B)
> 관련 ADR: [ADR-0008](../adr/0008-audit-log-architecture-v2.md) "Append-only 3중 방어선"
> 관련 자료: [SCHEMA_DESIGN.md](../../butterfly-reporting/docs/SCHEMA_DESIGN.md), [db/grants/README.md](../../butterfly-reporting/db/grants/README.md)

## 1. 요약

`audit_logs`의 append-only 보호 3중 방어선 중 ② **DB 권한 분리**를 운영 환경에 적용한다.

| 방어선 | 현재 상태 | 본 제안 후 |
|---|---|---|
| ① DB 트리거 (UPDATE/DELETE 시 SQLSTATE 45000) | ✅ 적용 (V4 마이그레이션, S14P31B101-121) | ✅ 유지 |
| ② **GRANT으로 INSERT/SELECT only** | ❌ **현재 미적용** — DB 계정이 모든 권한 보유 | ✅ **본 제안 적용** |
| ③ 애플리케이션 setter 미제공 + `@Immutable` | ✅ 적용 (S14P31B101-125) | ✅ 유지 |

② 미적용 상태에서는 코드 결함이나 운영자 실수로 트리거를 우회한 직접 SQL이 들어오면 audit 변조가 가능하다. ②가 추가되면 권한 부족 에러로 트리거 도달 전에 차단된다.

## 2. 배경

### 2.1 현재 운영 계정 상황

- 애플리케이션이 `butterfly` 계정으로 DB에 접속한다.
- `butterfly` 계정은 reporting 모듈 모든 테이블에 ALL PRIVILEGES 보유.
- `audit_logs`도 마찬가지 — DELETE, UPDATE 모두 가능 (트리거가 막지만, 트리거 우회 가능성 있음).

### 2.2 트리거만으로는 부족한 이유

- 트리거는 DML 단계에서 작동하지만, `SET sql_log_bin = 0` 후 권한 있는 계정이 트리거를 일시 비활성화하면 우회 가능.
- DBA 작업 실수, 운영 도구 잘못된 사용, 계정 자격 증명 유출 등 시나리오에서 트리거만으로는 무력화될 수 있다.
- GRANT으로 권한 자체를 회수하면 트리거 도달 전 차단된다 — **defence in depth**.

### 2.3 외부 의존

- DBA 부재 환경 — 본 제안은 팀 합의 후 사용자(분석/감사/로깅 담당)가 운영 DB에 적용한다.
- `S14P31B101-62` (시크릿 매니저) 합의 결과에 따라 비밀번호 주입 방식이 결정된다 — 본 제안은 시크릿 매니저 도입을 전제.

## 3. 적용안

### 3.1 계정 분리

| 계정 | 역할 | audit_logs 권한 | 다른 테이블 권한 |
|---|---|---|---|
| `app_user@%` | 애플리케이션 모든 도메인 작업 | **INSERT, SELECT only** | 도메인별 정상 권한 (status 전이 UPDATE 등) |
| `audit_viewer@%` (선택) | OWNER/ADMIN 감사 조회 화면 전용 | **SELECT only** | 다른 테이블 접근 불가 |
| `butterfly@%` | (기존 계정, 마지막에 권한 회수) | — | — |

`audit_viewer`는 v1에서 도입 여부를 운영 단계에서 재결정 — 감사 조회 트래픽이 분석 트래픽과 분리될 가치가 보이면 도입, 그렇지 않으면 `app_user` 단일 계정으로 운영.

### 3.2 GRANT 스크립트 위치

자세한 SQL은 본 PR에서 정착되는 다음 파일들을 참고:

- [`db/grants/app_user.sql`](../../butterfly-reporting/db/grants/app_user.sql)
- [`db/grants/audit_viewer.sql`](../../butterfly-reporting/db/grants/audit_viewer.sql)
- [`db/grants/revoke_legacy.sql`](../../butterfly-reporting/db/grants/revoke_legacy.sql)
- [`db/grants/README.md`](../../butterfly-reporting/db/grants/README.md)

### 3.3 datasource 전환

운영 환경 application property:

```yaml
spring:
  datasource:
    username: app_user
    password: ${APP_USER_PASSWORD}   # 시크릿 매니저 주입
```

`APP_USER_PASSWORD`는 환경별 시크릿 매니저(예: AWS Secrets Manager, Vault)에서 부팅 시점에 주입한다 — application.yml에 평문 비밀번호를 적지 않는다.

## 4. 적용 절차 (zero-downtime)

기존 `butterfly` 계정을 즉시 회수하지 않고 단계별로 전환한다 — 어느 단계에서 문제 발생 시 롤백이 가능하도록.

### 단계 1: 새 계정 생성 (영향 0)

- DBA가 dev 환경에서 `app_user.sql`, (선택) `audit_viewer.sql` 실행.
- 기존 `butterfly` 계정은 그대로 — 운영 영향 없음.
- 검증: `mysql -u app_user -p$APP_USER_PASSWORD -e "SELECT 1"` 부팅 가능 확인.

### 단계 2: dev 환경 datasource 전환

- dev application property에서 datasource user를 `app_user`로 변경.
- 통합 테스트 실행:
  - 정상 INSERT/SELECT 동작
  - audit_logs UPDATE/DELETE 시도 → 권한 부족 에러 (트리거 도달 전, MySQL ER_TABLEACCESS_DENIED_ERROR)
  - 다른 테이블의 status 전이 UPDATE 정상 동작
- 24~48시간 모니터링 — 운영 로그에서 권한 관련 에러 카운트 확인.

### 단계 3: prod 환경 datasource 전환 (점진 배포)

- prod 환경에서 동일 GRANT 적용.
- 일부 인스턴스(예: 1/N 카나리)부터 datasource 전환 → 메트릭 확인 → 전체 롤아웃.
- 전환 중 메트릭 모니터링:
  - audit 권한 에러 카운트 (정상이면 0)
  - INSERT 카운트 (이전과 동등)
  - 응답 시간 변화

### 단계 4: 안정화 (1~2주)

- prod 전체 인스턴스가 `app_user`로 동작.
- audit 권한 에러 메트릭 0 유지 확인.
- 에러 발생 시 롤백(단계 5b) 후 원인 조사.

### 단계 5a: 기존 계정 권한 회수 (안정화 후)

- DBA가 [`revoke_legacy.sql`](../../butterfly-reporting/db/grants/revoke_legacy.sql) 적용.
- 기존 `butterfly` 계정은 reporting 모듈 테이블에 접근 불가가 된다.
- 본 단계 후에는 어떤 코드 경로도 `butterfly` 계정으로 audit_logs를 변경할 수 없다.

### 단계 5b: (롤백 옵션) datasource 원복

- 단계 2/3에서 문제 발견 시 application property를 원래 `butterfly` 계정으로 되돌린다.
- 새 계정(`app_user`/`audit_viewer`)는 그대로 둔다 — 다음 시도에서 재사용.
- 단계 5a까지 갔다가 롤백이 필요하면 GRANT 재부여 스크립트가 추가로 필요하다 — 본 단계는 매우 신중히 진행.

## 5. 통합 테스트 계획

### 5.1 목표

CI에서 GRANT 효과를 자동 검증해, 운영 적용 후에도 회귀를 막는다.

### 5.2 구성

- Testcontainers MySQL 기동 시 init script로 [`app_user.sql`](../../butterfly-reporting/db/grants/app_user.sql)을 적용.
- 통합 테스트가 `app_user` 계정으로 connection 생성 후 다음을 검증:
  - INSERT/SELECT 정상 동작
  - `UPDATE audit_logs SET ...` → MySQL access denied (SQLSTATE 42000) 발생 — **트리거 도달 전**
  - `DELETE FROM audit_logs ...` → 동일하게 access denied
- 본 검증은 173-B 적용 시 추가하는 통합 테스트로 정착 — 본 173-A에서는 GRANT 스크립트 정착까지만.

### 5.3 기존 테스트와의 관계

- [`AuditLogTriggerIntegrationTest`](../../butterfly-reporting/src/test/java/com/b101/butterfly/reporting/audit/repository/AuditLogTriggerIntegrationTest.java) (S14P31B101-125)는 root 계정으로 트리거 동작을 검증한다 — 이는 그대로 유지.
- 본 제안의 새 테스트는 `app_user` 계정으로 권한 부족 에러를 검증한다 — 트리거 검증과 직교.
- 두 테스트가 모두 통과하면 ① + ② 방어선이 모두 작동.

## 6. 롤백 절차

| 단계 도달 | 롤백 방법 |
|---|---|
| 단계 1 (계정 생성만) | `DROP USER 'app_user'@'%'`, `DROP USER 'audit_viewer'@'%'` |
| 단계 2 (dev datasource 전환) | application property 원복 → 부팅 시 즉시 효과 |
| 단계 3 (prod 점진 배포 중) | 카나리 인스턴스만 원복 → 모니터링 → 전체 원복 결정 |
| 단계 4 (prod 안정화) | application property 원복 + 재배포 |
| 단계 5a (revoke_legacy 적용 후) | `butterfly` 계정에 GRANT 재부여 (별도 스크립트 작성 필요) — 가장 위험한 시나리오 |

## 7. 적용 체크리스트 (173-B에서 사용)

운영 적용 시 173-B 담당자가 아래 체크리스트를 따라간다:

- [ ] DBA(또는 권한 보유자)와 적용 시점 합의
- [ ] dev: `app_user.sql` 적용 → 부팅 확인
- [ ] dev: datasource 전환 → 통합 테스트 통과 → 24h 모니터링
- [ ] prod: `app_user.sql` 적용
- [ ] prod: 카나리 인스턴스 datasource 전환 → 메트릭 정상 확인
- [ ] prod: 전체 인스턴스 datasource 전환
- [ ] 1주 안정화 모니터링
- [ ] (선택) `audit_viewer.sql` 적용
- [ ] `revoke_legacy.sql` 적용 — 기존 계정 회수
- [ ] application.yml의 비밀번호 평문 제거 → 시크릿 매니저 참조로 통일

## 8. 알려진 이슈 / 후속 결정

- **시크릿 매니저 의존**: `S14P31B101-62`에서 도구 합의가 끝나야 단계 2 이상 진행 가능. 그 전까지 dev 환경은 환경 변수 직접 주입으로 우회 가능하나 prod는 합의 필수.
- **`audit_viewer` 도입 여부**: v1 운영 데이터를 일정 기간 본 후 결정. 본 제안에 SQL은 정착하되 적용은 선택.
- **백업·복원 도구 권한**: mysqldump/Percona xtrabackup 등 백업 도구는 별도 계정이 필요할 수 있다 — 본 제안 범위 외, 운영팀과 합의.
- **장기적 인증 모듈 도입**: 인증 모듈이 정착되면 datasource 전환과 별개로 SecurityFilterChain 측에서도 audit 조회 권한이 강화된다 ([InternalTokenGuard](../../butterfly-reporting/src/main/java/com/b101/butterfly/reporting/notification/controller/InternalTokenGuard.java) 패턴 참고).

## 9. 결정 요청

본 제안을 다음 항목에 대해 합의받고자 한다:

1. **계정 구조** — `app_user` + (선택) `audit_viewer` 분리 채택?
2. **단계별 zero-downtime 절차** — 4단계 + 안정화 + 회수 흐름 채택?
3. **`audit_viewer` 도입 여부** — v1에서 켤지/미루는지?
4. **시크릿 매니저** — `S14P31B101-62`와 함께 진행하는 일정 합의 가능한지?
5. **적용 일정** — dev/prod 적용 시점 (예: 시크릿 매니저 합의 후 2주 내)?

합의 결과는 본 문서 §"변경 이력"에 기록한다.

## 10. 변경 이력

| 일자 | 내용 | 작성자 |
|---|---|---|
| 2026-05-04 | 최초 작성 (S14P31B101-173 / 173-A) | 이재영 |
