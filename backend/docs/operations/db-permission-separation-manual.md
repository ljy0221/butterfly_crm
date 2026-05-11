# DB 권한 분리 — 팀원 매뉴얼

> Owner: 이재영
> 최초 작성: 2026-05-11
> 관련 Jira: `S14P31B101-182` (감사 로그 DB 권한 분리 운영 적용)
> 관련 문서: [audit-db-permission-rollout.md](./audit-db-permission-rollout.md), [04-audit-logs-db-permission-separation.md](../proposals/04-audit-logs-db-permission-separation.md), [log-policy.md](./log-policy.md), [backup-recovery-policy.md](./backup-recovery-policy.md)
> 관련 ADR: [ADR-0008](../adr/0008-audit-log-architecture-v2.md) Append-only 3중 방어선
> 합의 상태: 2026-05-11 팀 합의 완료 (제안서 발송 후 별도 의견 없음 → 그대로 진행, 본 문서로 전파)

본 문서는 `audit_logs` append-only 보호의 **② DB 권한 분리** 가 적용된 후 **개발자가 알아야 할 변화·금지 패턴·트러블슈팅** 을 한곳에 모은다. 운영 적용 절차는 [audit-db-permission-rollout.md](./audit-db-permission-rollout.md) 가 별도 다룬다.

---

## 1. 무엇이 바뀌었나 (요약)

| 항목 | Before | After |
|---|---|---|
| DB 사용자 계정 | 단일 `butterfly@%` (모든 권한) | 분리: `app_user@%` (제한) + (선택) `audit_viewer@%` |
| `audit_logs` 권한 | `butterfly` 전체 권한 | `app_user`: **INSERT + SELECT 만** (UPDATE/DELETE 권한 없음) |
| 다른 테이블 UPDATE | `butterfly` 전체 | `app_user`: 비즈니스 필요 테이블만 (notification_logs/집계 5종/정합성 등 — UPDATE 허용) |
| 운영 환경 datasource | `butterfly` | `app_user` (시크릿 매니저에서 비번 주입) |
| 기존 `butterfly` 계정 | 활성 | 권한 회수 (`revoke_legacy.sql`) — DROP USER 는 안 함, 비활성 상태 |

### Append-only 3중 방어선 (ADR-0008)

1. **트리거**: `audit_logs` UPDATE/DELETE 시도 시 BEFORE 트리거가 SIGNAL 발생 (S14P31B101-121, 125)
2. **DB 권한 분리**: 본 작업 ← 트리거 도달 전에 권한 부족(`1142`) 에러로 차단
3. **애플리케이션 Immutability**: Hibernate `@Immutable` + setter 미제공 (S14P31B101-125)

→ 셋 중 하나만 무력화되어도 다른 둘이 차단. 본 매뉴얼은 ② 측 변경 사항을 전파.

---

## 2. 개발자 영향 (반드시 확인)

### 2.1 로컬 개발 환경

#### 로컬 docker-compose

기존 `butterfly` 계정으로 MySQL 컨테이너에 직접 접속하던 습관이 있다면:

```bash
# Before (계속 동작은 함 — 로컬 dev MySQL은 본 작업 범위에서 root 사용 유지)
docker exec -it mysql mysql -u butterfly -p butterfly

# After (권장)
docker exec -it mysql mysql -u app_user -p butterfly
# 또는 디버깅용 root 접속
docker exec -it mysql mysql -u root -p butterfly
```

> **로컬 dev MySQL 컨테이너**: 본 권한 분리는 **dev 공유 환경 + stage + prod** 에 적용. 개발자 로컬의 docker-compose MySQL 은 단순화 위해 `root`/`butterfly` 그대로 유지 가능 — 단 통합 테스트는 `app_user` 로 실행 (§2.3 참고).

#### application-local.yml (로컬 IDE 실행 시)

`spring.datasource.username` 을 `app_user` 로 설정하면 본 권한 분리가 로컬에서도 검증됨:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/butterfly
    username: app_user  # 권한 분리 검증용
    password: ${APP_USER_PASSWORD:butterfly-local-dev}  # 로컬은 평문 가능
```

### 2.2 코드에서 절대 하지 말아야 할 것

#### ❌ `audit_logs` 직접 UPDATE/DELETE

```java
// 금지 — Hibernate 가 막지 못해도 DB 가 권한 부족 에러
auditLogRepository.deleteById(id);            // ❌
em.createNativeQuery("UPDATE audit_logs ...").executeUpdate();  // ❌
auditLog.setDetail("...");  // ❌ — @Immutable + setter 미제공이라 일반 코드 흐름에선 불가
```

발생 시 에러:

```
java.sql.SQLException: UPDATE command denied to user 'app_user'@'%' for table 'audit_logs'
SQLState: 42000, ErrorCode: 1142
```

→ Audit 로그는 **immutable 이력**. 변경이 필요하면 새 row INSERT (`AuditLogService.record()` 호출).

#### ❌ 다른 모듈의 테이블 권한 가정

`app_user` 는 **reporting 모듈 테이블** 만 명시적으로 권한 부여됨 (app_user.sql 참고). 다른 모듈(`auth/tenant/segment/campaign/...`) 테이블은 각 모듈의 `db/grants/` 디렉토리에서 권한 관리하므로, **다른 모듈 테이블 직접 SELECT/UPDATE 시도 시 권한 부족 에러** 발생 가능.

→ 다른 모듈 데이터가 필요하면 **SPI / 도메인 서비스** 를 통해 접근 (ADR-0014 모듈 경계).

#### ❌ 로그/예외 메시지에 DB 자격 증명 노출

```java
log.error("DB 접속 실패: user=app_user password={}", password);  // ❌
```

→ 시크릿은 시크릿 매니저에서 주입, 어떤 형태로도 로그에 남기지 않음 (log-policy §4).

### 2.3 통합 테스트

#### TestContainers 환경

기본 TestContainers MySQL 은 root 로 띄워지므로 권한 검증이 누락된다. **권한 검증 시나리오** 가 필요한 테스트는 `app_user` 로 실행:

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditLogPermissionTest extends AbstractMySqlContainerTest {

    @BeforeAll
    static void setupAppUser() {
        // TestContainers MySQL 에 app_user 생성 + 권한 부여
        // 또는 application.yml 의 datasource user 를 app_user 로 동적 설정
    }

    @Test
    @DisplayName("app_user 계정으로 audit_logs UPDATE 시도 → SQLException")
    void appUserCannotUpdateAuditLogs() {
        // 검증 절차
    }
}
```

> **CI 통합 테스트** 는 본 적용 후 점진적으로 `app_user` 계정으로 전환 — 회귀 발견 즉시 fix. CI 환경 변수 갱신은 별도 PR.

### 2.4 운영 환경에서 root 사용 금지

본 적용 후 운영 환경에서 **root 자격 증명을 사용한 직접 DB 접근**은 다음 경우에만 허용:

| 허용 케이스 | 절차 |
|---|---|
| Flyway 마이그레이션 실행 | DBA 또는 운영 책임자, 기록 남김 |
| GRANT/REVOKE 운영 (권한 변경) | DBA 책임, audit 기록 |
| 장애 복구 (R-02 등) | 백업/복구 정책 §4.2 절차 따름 |

→ 일상 운영 트래픽은 모두 `app_user` 로. 운영자가 `audit_logs` 조회만 필요하면 `audit_viewer` 계정 사용.

---

## 3. 트러블슈팅

### 3.1 `1142: command denied to user 'app_user'@'%' for table 'X'`

가장 흔한 에러. 원인은 두 가지:

**원인 A**: 정당한 권한이 누락됨 (app_user.sql 에서 빠짐)

```sql
-- 확인: 현재 app_user 가 어떤 권한 가지고 있는지
SHOW GRANTS FOR 'app_user'@'%';
```

→ 보고: 본인(이재영) 또는 DBA 에게 Jira 댓글 + 테이블 이름·필요한 권한 명시

**원인 B**: 코드가 audit_logs 를 잘못 변경하려 함 (의도된 차단)

→ 코드를 수정. `AuditLogService.record()` 외 경로로 audit 변경 시도 금지

### 3.2 `Access denied for user 'app_user'@'localhost'`

`app_user` 계정 미존재 또는 비번 불일치. 환경별 확인:

```bash
# dev/stage/prod: 시크릿 매니저에서 비번 회수
aws secretsmanager get-secret-value --secret-id butterfly/dev/app_user

# 로컬 docker-compose: docker-entrypoint-initdb.d/ 의 init.sql 적용 확인
docker exec -it mysql mysql -u root -p -e "SELECT user, host FROM mysql.user WHERE user='app_user'"
```

### 3.3 통합 테스트가 로컬에서 통과하는데 CI 에서 실패

CI 환경의 MySQL 컨테이너 user 가 root 인 반면, 코드는 app_user 권한 가정. CI 환경 변수 `SPRING_DATASOURCE_USERNAME=app_user` 설정 누락 가능성.

→ `.gitlab-ci.yml` 의 integration-test 잡 변수 확인 + CI 적용 PR 머지 여부 확인.

### 3.4 운영 DB 비밀번호를 모름

**절대 평문 비밀번호를 Slack/Mattermost/이메일에 공유하지 않음**. 시크릿 매니저에서 회수:

```bash
aws secretsmanager get-secret-value --secret-id butterfly/{env}/app_user --query SecretString --output text
```

→ 권한이 없다면 **운영 책임자에게 1:1 요청** (역할 분담은 [백업/복구 정책 §5](./backup-recovery-policy.md#5-책임자역할-분담) 참고).

---

## 4. 적용 일정 (계획)

| 단계 | 환경 | 시점 (계획) | 절차 |
|---|---|---|---|
| 1 | dev | **5/13 (화)** | `app_user.sql` 적용 + datasource 전환 + smoke test |
| 2 | dev | 5/13 이후 모니터링 1~2일 | 통합 테스트·로그 점검 |
| 3 | stage | **5/14 (수)** | 동일 절차 |
| 4 | prod | **5/16 (금)** Code Freeze 직후 | 동일 절차 + 백업 1회 우선 |
| 5 | 전 환경 | 안정화 후 (5/20~) | `revoke_legacy.sql` — 기존 butterfly 계정 권한 회수 |

> 단계 1~4 사이 일정 변경은 본 문서에 갱신. **단계 5 는 데모 발표(5/22) 이후로 미룰 수도 있음** — 발표 중 회귀 발견 시 롤백 여유 확보용.

---

## 5. 진행 시 체크리스트

운영 적용 진행자가 환경별로 따라가는 체크리스트.

### 5.1 적용 전

- [ ] 백업 정책에 따른 직전 백업 1건 존재 ([backup-recovery-policy.md §2](./backup-recovery-policy.md))
- [ ] 시크릿 매니저에 `butterfly/{env}/app_user` 비번 등록됨
- [ ] (선택) `butterfly/{env}/audit_viewer` 비번 등록
- [ ] 본 매뉴얼 팀 채널 전파 완료 (Mattermost #dev 등 + 본 PR 머지)

### 5.2 적용 (각 환경)

- [ ] `app_user.sql` 실행 (envsubst 로 비번 주입)
- [ ] (선택) `audit_viewer.sql` 실행
- [ ] `app_user` 권한 검증: `SHOW GRANTS FOR 'app_user'@'%';` — 본 매뉴얼 §1 표와 일치
- [ ] application-{env}.yml 의 datasource user → `app_user` 변경 (이전 값 백업)
- [ ] 시크릿 주입 검증 (앱 기동 후 actuator/health UP)
- [ ] smoke test: 시나리오 #1 로그인 + #2 이벤트 적재 + 감사 로그 1건 조회

### 5.3 검증

- [ ] `app_user` 로 `audit_logs` UPDATE 시도 → 1142 에러 (의도된 차단 확인)
- [ ] `app_user` 로 `audit_logs` INSERT 정상
- [ ] 다른 테이블 (notification_logs, 집계 5종 등) UPDATE 정상
- [ ] 감사 로그 조회 화면 (`/api/v1/audit-logs`) 정상 응답
- [ ] 운영 로그에 `1142` 에러 없음 (정상 동작)

### 5.4 정착 (안정화 후, 단계 5)

- [ ] `revoke_legacy.sql` 적용 → 기존 `butterfly` 계정 권한 회수
- [ ] `butterfly` 계정 로그인 시도 → access denied (의도된 차단 확인)
- [ ] 본 문서의 "Before" 컬럼이 더 이상 유효하지 않음을 명시 (선택)

---

## 6. 롤백 절차

운영 적용 후 회귀 발견 시:

### 6.1 datasource 만 즉시 롤백

```yaml
# application-{env}.yml
spring:
  datasource:
    username: butterfly  # 임시 복귀
```

→ 앱 롤링 재시작. `app_user` 계정 자체는 살아있으므로 다음 시도 시 복구 가능.

### 6.2 권한 자체 롤백 (revoke_legacy.sql 적용 후 회귀 발견)

```sql
-- 1. butterfly 계정에 권한 다시 부여 (백업해둔 Before 권한 표 참고)
GRANT INSERT, SELECT, UPDATE, DELETE ON butterfly.* TO 'butterfly'@'%';

-- 2. 시크릿 매니저의 비번이 정확한지 확인 후
-- 3. application.yml datasource user 를 butterfly 로 변경 + 재시작
```

→ 이 시점에서 audit_logs 의 DB 레벨 보호는 일시 무효화. 트리거 + @Immutable 두 방어선만 남음. 24시간 내 재적용 또는 별도 결정.

---

## 7. 관련 문서

- [audit-db-permission-rollout.md](./audit-db-permission-rollout.md) — 운영 적용 절차 (현 문서가 매뉴얼이라면 그쪽은 실행 가이드)
- [04-audit-logs-db-permission-separation.md](../proposals/04-audit-logs-db-permission-separation.md) — 173-A 제안서
- [log-policy.md](./log-policy.md) — 운영/감사 로그 분리
- [backup-recovery-policy.md](./backup-recovery-policy.md) — A 등급 (audit_logs 3년 보존)
- [ADR-0008](../adr/0008-audit-log-architecture-v2.md) — Append-only 3중 방어선
- [GRANT 스크립트](../../butterfly-reporting/db/grants/) — `app_user.sql` / `audit_viewer.sql` / `revoke_legacy.sql`

---

## 8. 변경 이력

| 날짜 | 변경 | 작성자 |
|---|---|---|
| 2026-05-11 | 초안 작성 (S14P31B101-182 매뉴얼 정착) | 이재영 |
| TBD | dev 적용 후 발견 사항 보강 | 적용 진행자 |
| TBD | stage/prod 적용 후 보강 | 적용 진행자 |
| TBD | revoke_legacy 적용 후 §1 Before 컬럼 비활성화 | 적용 진행자 |
