# audit_logs DB 권한 분리 — 운영 적용 진행 내역

> Owner: 이재영
> 최초 작성: 2026-05-04
> 상태: **진행 중 (in progress)**
> 관련 Jira: `S14P31B101-183` (173-B, 본 작업), `S14P31B101-173` (173-A, 제안서 — 머지 완료)
> 관련 제안서: [04-audit-logs-db-permission-separation.md](../proposals/04-audit-logs-db-permission-separation.md)
> 관련 ADR: [ADR-0008](../adr/0008-audit-log-architecture-v2.md) "Append-only 3중 방어선"

본 문서는 [제안서 #04](../proposals/04-audit-logs-db-permission-separation.md)의 합의 결과를 받아 실제 운영 적용까지 가는 **작업 계획**과 **진행 상세 내역**을 정착한다. 제안서는 "무엇을·왜"를 정의하고, 본 문서는 "누가·언제·어떻게 했는지"를 기록한다.

각 단계의 결과(수행 일자, 검증 메트릭, 관찰 사항)는 §"진행 로그"에 시간순으로 기록한다.

---

## 1. 합의 결과 (2026-05-04)

제안서 #04에 대한 팀 리뷰 결과 별도 의견 없이 원안대로 합의되었다 (사용자 보고, 2026-05-04). 합의 항목:

| 항목 | 결정 |
|---|---|
| 계정 구조 | `app_user` 채택. `audit_viewer`는 v1에서는 보류 (운영 데이터 본 후 재결정) |
| 적용 절차 | 4단계 zero-downtime + 안정화 + 회수 (제안서 §"적용 절차"대로) |
| 시크릿 매니저 | `S14P31B101-62`와 함께 진행. 합의 결과를 본 문서 §3에서 참조 |
| 적용 일정 | dev: 시크릿 매니저 합의 후 즉시 / prod: dev 안정화 7일 후 |

`audit_viewer.sql`은 본 PR에서 그대로 두되 운영 적용은 보류. 도입 시점이 오면 본 문서에 절차 추가.

---

## 2. 작업 단계

제안서 §"적용 절차"의 4단계 + 1단계(안정화) + 1단계(회수)를 본 문서에서 구체적인 체크리스트와 결과 기록란으로 확장한다.

### 단계 0 — CI 통합 테스트 정착 (본 PR)

**책임자**: 이재영 (S14P31B101-183 본 PR)
**목적**: GRANT 효과를 코드로 자동 검증. 운영 적용 전에 CI에서 회귀를 잡는다.

작업 항목:

- [x] `db/grants/app_user.sql` 변환 — Testcontainers MySQL이 init script로 적용할 수 있도록 envsubst 없이 동작하는 테스트 변형(`audit_db_permission_test_init.sql`)을 테스트 리소스에 둔다.
- [x] `AuditDbPermissionIntegrationTest` 추가 — `app_user` 계정 connection으로:
  - INSERT/SELECT 정상 동작
  - UPDATE/DELETE 시도 → access denied (트리거 도달 전)
- [x] CI(`./gradlew :butterfly-reporting:integrationTest`)에서 통과 확인

운영 DB는 건드리지 않는다 — 본 단계는 코드만.

**결과**: §"진행 로그" 참고.

### 단계 1 — 새 계정 생성 (운영 영향 0)

**책임자**: DBA 권한 보유자 (현재는 사용자 본인이 dev/prod root로 직접 실행)
**대기 의존**: `S14P31B101-62` 비밀번호 주입 방식 합의

작업 항목:

- [ ] dev 환경: 시크릿 매니저에 `butterfly/dev/app_user` secret 등록 (S14P31B101-62 결정 도구 사용)
- [ ] dev 환경: `app_user.sql`을 envsubst로 비밀번호 주입 후 적용
- [ ] dev 환경: `mysql -u app_user -p$APP_USER_PASSWORD -e "SELECT 1 FROM butterfly.audit_logs LIMIT 0"` 부팅 확인
- [ ] prod 환경: dev 안정화 후 동일 절차

**기존 `butterfly` 계정은 그대로 둔다** — 단계 4까지 회수하지 않는다.

**관찰 메트릭**: 새 계정 생성 시 운영 영향 없어야 함 (기존 트래픽 그대로).

### 단계 2 — dev 환경 datasource 전환

**책임자**: 이재영
**대기 의존**: 단계 1 완료, 시크릿 매니저 주입 동작 확인

작업 항목:

- [ ] `application-dev.yml` 또는 `application.properties` 의 `spring.datasource.username` → `app_user` 로 변경
- [ ] 비밀번호: `${APP_USER_PASSWORD}` 환경 변수 또는 시크릿 매니저 reference로 변경
- [ ] dev 부팅 후 통합 테스트 스위트 실행 — 모든 도메인 정상 동작 + audit_logs INSERT 정상
- [ ] 24~48시간 모니터링: audit 권한 에러 카운트(목표 0), INSERT 카운트(이전과 동등)

**롤백 트리거**: 권한 에러 카운트 > 0 또는 INSERT 카운트 ↓ 30% 이상 → application property 원복.

### 단계 3 — prod 환경 datasource 전환 (점진 배포)

**책임자**: 이재영 + 운영 인스턴스 배포 권한자
**대기 의존**: dev 단계 2 안정화 (최소 48h)

작업 항목:

- [ ] prod 환경 시크릿 매니저에 `butterfly/prod/app_user` secret 등록
- [ ] prod GRANT 적용 (`app_user.sql`)
- [ ] 카나리 인스턴스(전체의 10~20%) datasource 전환 → 메트릭 모니터링 1시간
  - audit 권한 에러: 0
  - INSERT 카운트: 이전 시간대와 비교해 ±5% 이내
  - 응답 시간 p99: 변화 없음
- [ ] 점진 롤아웃 (50% → 100%)
- [ ] 전체 전환 완료 후 6시간 모니터링

**롤백 트리거**: 카나리 단계에서 메트릭 이상 → 즉시 application property 원복 + 원인 조사 후 재시도.

### 단계 4 — 안정화 (1~2주)

**책임자**: 이재영 (모니터링)

작업 항목:

- [ ] prod 전체 인스턴스가 `app_user`로 동작
- [ ] 7일간 일일 메트릭 리포트:
  - audit 권한 에러 카운트
  - INSERT 카운트
  - audit_logs row 증가량 (정상이면 이전 7일과 비교해 ±10% 이내)
  - 응답 시간 메트릭
- [ ] 14일까지 이상 없음 확인 → 단계 5a 진행

### 단계 5a — 기존 계정 권한 회수

**책임자**: DBA 권한 보유자

작업 항목:

- [ ] dev: `revoke_legacy.sql` 적용 → `butterfly` 계정으로 audit_logs 접근 불가 확인
- [ ] dev 환경에서 `butterfly` 계정 사용 흔적 없는지 grep (`grep -r "username:.*butterfly" application*.yml`)
- [ ] prod: `revoke_legacy.sql` 적용
- [ ] prod 환경에서 동일 검증

본 단계 후에는 어떤 코드 경로도 `butterfly` 계정으로 audit_logs를 변경할 수 없다. 적용 전 §"체크리스트"의 모든 항목을 만족해야 한다.

### 단계 5b — 롤백 시나리오 (참조용)

**언제 사용**: 단계 5a 후에도 결정적 회귀가 발견될 경우 (예: 수개월 뒤 발견된 코드 경로가 `butterfly` 계정 권한에 의존)

**절차**:

1. application property 원복 (`username: butterfly`)
2. `butterfly` 계정에 GRANT 재부여 — 현재 별도 스크립트 없음. 필요 시 `app_user.sql`을 변형해 사용
3. 단계 1부터 재진입

**예방**: 단계 4 안정화 기간을 충분히 길게(최소 14일) 가져가 롤백 가능성을 최소화한다.

---

## 3. 시크릿 매니저 결정 사항 (S14P31B101-62 연계)

> 본 절은 `S14P31B101-62`에서 합의된 결과를 받아 채워넣는다. 현재는 placeholder.

| 항목 | 합의 |
|---|---|
| dev 환경 도구 | (TBD — Vault / AWS Secrets Manager / k8s Secret 등) |
| prod 환경 도구 | (TBD) |
| Spring 통합 방식 | (TBD — `spring.config.import=...secretsmanager:...` 또는 환경 변수 주입) |
| secret 키 네이밍 | `butterfly/<env>/app_user` 권장 (제안서 §3.3) |
| 회전(rotation) 주기 | (TBD) |

**62 합의 결과가 정해지면** 본 문서를 갱신하고 단계 1 진행을 시작한다.

---

## 4. 체크리스트 (운영 적용 전 최종 확인)

단계 5a 적용 직전 마지막 게이트로 확인.

- [ ] CI에서 `AuditDbPermissionIntegrationTest` 통과 (단계 0)
- [ ] dev 환경에서 `app_user`로 7일 이상 안정 운영 (단계 4)
- [ ] prod 환경에서 `app_user`로 14일 이상 안정 운영 (단계 4)
- [ ] audit 권한 에러 메트릭 누적 0 유지
- [ ] audit_logs row 증가량이 이전 14일과 ±10% 이내 (분석 누락 가능성 점검)
- [ ] application.yml 어디에서도 `username: butterfly` 또는 평문 비밀번호가 검색되지 않음
- [ ] DBA 권한자와 단계 5a 적용 시점·롤백 절차 재확인

위 항목 중 하나라도 빠지면 단계 5a를 미룬다.

---

## 5. 진행 로그

| 일자 | 단계 | 내역 | 결과 |
|---|---|---|---|
| 2026-05-04 | 0 | CI 통합 테스트 정착 (본 PR `S14P31B101-183`) | 진행 중 — 테스트 추가 후 본 PR 머지로 단계 0 완료 |
| 2026-05-04 | 합의 | 제안서 #04 팀 리뷰 결과 별도 의견 없이 합의 | `audit_viewer` v1 보류, 나머지 원안대로 |
| (이후) | 1 | dev 새 계정 생성 | (기록 예정) |
| (이후) | 2 | dev datasource 전환 | (기록 예정) |
| (이후) | 3 | prod 점진 배포 | (기록 예정) |
| (이후) | 4 | 안정화 모니터링 | (기록 예정) |
| (이후) | 5a | 기존 계정 회수 | (기록 예정) |

각 단계 진행 시 본 표에 일자·내역·결과를 추가한다 — 단계 1~5a는 별도 PR로 진행 (운영 변경이라 코드 PR과 분리).

---

## 6. 인수 조건과 단계 매핑 (S14P31B101-183)

| 인수 조건 | 만족 시점 |
|---|---|
| `app_user@%` 계정이 운영 DB(dev/prod)에 존재한다 | 단계 1 완료 |
| `audit_logs`에 대해 `app_user`는 INSERT, SELECT 권한만 보유한다 | 단계 1 완료 (CI에서도 단계 0으로 검증됨) |
| `app_user`로 audit_logs UPDATE/DELETE 시도 시 권한 부족 에러 (트리거 도달 전) | 단계 0 (CI) + 단계 1 (운영 검증) |
| 운영 환경 datasource가 `app_user` 계정 사용 (root/butterfly 사용 금지) | 단계 3 완료 + 단계 5a로 강제됨 |
| DB 계정 비밀번호는 환경별 시크릿 매니저 사용 | 단계 1~3 (S14P31B101-62 합의 후) |
| 통합 테스트가 권한 부족 시나리오를 검증한다 | 단계 0 (본 PR) |

본 PR(183) 자체는 단계 0(CI 통합 테스트) + 본 문서 정착으로 머지 가능. 인수 조건의 운영 부분은 단계 1~5a에서 본 문서를 따라 진행하며, 각 단계 완료 시 §5 진행 로그를 갱신한다. 모든 단계가 완료되면 173-B 티켓을 닫는다.
