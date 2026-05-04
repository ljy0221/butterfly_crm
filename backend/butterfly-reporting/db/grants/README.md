# db/grants — 운영 DB 권한 분리 스크립트

> Owner: 이재영
> 관련 Jira: `S14P31B101-173`(173-A, 본 디렉토리), `S14P31B101-183`(173-B, 운영 적용)
> 관련 ADR: [ADR-0008](../../../docs/adr/0008-audit-log-architecture-v2.md) "Append-only 3중 방어선"
> 관련 제안서: [04-audit-logs-db-permission-separation.md](../../../docs/proposals/04-audit-logs-db-permission-separation.md)

본 디렉토리는 `audit_logs` append-only 보호의 ② DB 권한 분리(애플리케이션 코드 setter 미제공·트리거에 이은 3중 방어선)를 운영 환경에 적용할 때 그대로 실행 가능한 SQL 모음이다.

Flyway 마이그레이션이 아닌 **DBA 권한으로 한 번 실행**하는 운영 스크립트다 — 환경별로 다르게 적용해야 하므로 마이그레이션화하지 않는다.

## 파일 구성

| 파일 | 시점 | 책임 |
|---|---|---|
| `app_user.sql` | 새 권한 체계 도입 1단계 | `app_user@%` 생성 + 테이블별 GRANT (audit_logs는 INSERT/SELECT만) |
| `audit_viewer.sql` | 1단계 (선택, OWNER/ADMIN 감사 조회 전용) | `audit_viewer@%` 생성 + audit_logs SELECT only |
| `revoke_legacy.sql` | 마지막 단계 (검증 통과 후) | 기존 `butterfly` 계정 권한 회수 |

## 적용 절차 (zero-downtime)

자세한 설명은 [04-audit-logs-db-permission-separation.md](../../../docs/proposals/04-audit-logs-db-permission-separation.md) §"적용 절차" 참고.

요약:

1. `app_user.sql`, `audit_viewer.sql` 적용 — 새 계정 생성, 기존 `butterfly`는 그대로
2. dev 환경 datasource user를 `app_user`로 전환, 통합 테스트로 검증
3. prod 환경 datasource 전환 (점진 배포: 일부 인스턴스 → 전체)
4. 모니터링: `audit_logs` UPDATE/DELETE 시도 에러 카운트 추적, 정상 INSERT 카운트 비교
5. 안정화 후 `revoke_legacy.sql` 적용 — 기존 계정 권한 회수

## 비밀번호

`<APP_USER_PASSWORD>` 등 placeholder는 적용 시점에 시크릿 매니저(`S14P31B101-62`)에서 주입한다.

스크립트 안에 평문 비밀번호를 적지 않는다. 적용 명령은 다음과 같이 전개한다:

```bash
APP_USER_PASSWORD=$(aws secretsmanager get-secret-value --secret-id butterfly/dev/app_user --query SecretString --output text) \
  envsubst < app_user.sql | mysql -h $DB_HOST -u root -p$ROOT_PASSWORD butterfly
```

## 롤백

각 파일에 대응되는 롤백 가이드가 제안서 §"롤백 절차"에 있다 — 본 디렉토리에는 별도 롤백 스크립트를 두지 않는다 (실수로 적용되는 위험을 피함).
