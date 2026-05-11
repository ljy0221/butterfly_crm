# Butterfly 백업/복구 운영 정책

> Owner: 이재영
> 최초 작성: 2026-05-11
> 관련 Jira: `S14P31B101-66` (백업/복구 운영정책 및 리허설 계획 수립)
> 관련 문서: [log-policy.md](./log-policy.md), [audit-db-permission-rollout.md](./audit-db-permission-rollout.md), [kpi-and-attribution.md](./kpi-and-attribution.md)
> 관련 ADR: [ADR-0008](../adr/0008-audit-log-architecture-v2.md), [ADR-0009](../adr/0009-audit-log-relocation-to-reporting.md)

본 문서는 Butterfly 의 데이터·설정·시크릿에 대한 **무엇을 / 언제 / 어떻게 / 누가** 백업·복구할지를 규정한다. 장애 자체보다 **복구 절차 부재가 더 큰 리스크** 이므로, 데모/운영 모두 본 정책을 기준으로 한다.

> Jira 66 본문은 PostgreSQL/RabbitMQ 를 가정했으나 실제 스택은 **MySQL 8.x + Kafka** (RabbitMQ 제거 결정, 2026-05-11). 본 문서는 실 스택 기준으로 작성한다.

---

## 1. 백업 대상 분류

### A. 절대 손실 금지 (RPO < 5분, RTO < 1시간)

| 항목 | 저장소 | 사유 |
|---|---|---|
| `audit_logs` | MySQL (append-only) | 컴플라이언스 — 최소 1년, 최대 3년 보존 (log-policy §1). 트리거 + DB GRANT + Hibernate `@Immutable` 3중 방어선 ([ADR-0008](../adr/0008-audit-log-architecture-v2.md)) |
| `notification_logs` | MySQL | 발송 원장 — 분석/정합성 검증의 진실 공급원 (ADR-0007) |
| `user_action_logs` | MySQL | OPEN/CLICK/CONVERT 이벤트 원본 — attribution 윈도우 30일 안의 모든 row |
| `projects.public_api_key` (SHA-256 해시) | MySQL | 키 분실 시 모든 SDK 재발급 필요 (ADR-0017) |
| `members`, `tenants` | MySQL | 멀티테넌트 계정 — 분실 시 로그인 불가 |
| 시크릿 매니저 (`butterfly/{env}/*`) | AWS Secrets Manager 또는 동등 | DB 비번/JWT 시크릿/API Key 등 — 분실 시 모든 환경 재구성 |

### B. 중요 (RPO < 1시간, RTO < 4시간)

| 항목 | 저장소 | 사유 |
|---|---|---|
| `campaigns`, `campaign_executions`, `campaign_messages` | MySQL | 발송 이력 재현 (`campaign_messages` 가 발송 직전 본문을 보존) |
| `segments`, `segment_*` | MySQL | 세그먼트 정의 — 분실 시 재정의 필요하나 재현은 단순 |
| `message_templates` | MySQL | 템플릿 — 분실 시 운영자가 재작성 가능 (시간 비용 큼) |
| `campaign_stats_hourly` / `campaign_stats_daily` 등 집계 4종 | MySQL | `force=true` 재집계로 복구 가능 — 원장(A 등급)이 있으면 재계산 |
| `send_reconciliations` | MySQL | 정합성 검증 결과 — 재배치 호출로 재생성 가능 |
| `system_health_snapshots` | MySQL | 메트릭 시계열 — 손실 허용 (외부 Grafana 보존 권장) |

### C. 재현 가능 (RPO/RTO 제약 없음)

| 항목 | 저장소 | 사유 |
|---|---|---|
| Redis 캐시 (`auth:permission:*`, `auth:blacklist:*` 등) | Redis | 캐시 만료/재빌드 가능 — JWT 블랙리스트는 비교적 짧은 TTL |
| Redis 레이트 리밋 카운터 (`apikey:*`) | Redis | sliding window — 최근 1분치만 유효 |
| Kafka 토픽 (notification-send-result, DLQ 등) | Kafka | 컨슈머 오프셋 + retention (3일 권장) |
| 운영 로그 (Loki/ELK) | 로그 수집 스택 | 30일 보존 (log-policy §5) |
| docker image (`ghcr.io/.../butterfly-api:*`) | GHCR | git 으로 재빌드 가능 |

### D. 설정·코드 (보호 자체보다 일관성)

| 항목 | 저장소 | 처리 |
|---|---|---|
| `application.yml`, `application-{env}.yml` | git (`backend/butterfly-api/src/main/resources/`) | git push 이력 자체가 백업 |
| 마이그레이션 SQL (`V*.sql`) | git | git push, baseline 통합 시점 박제 |
| `docker-compose.dev.yml`, `.gitlab-ci.yml`, K8s manifest | git | git push |
| ADR / 운영 문서 / KPI 정의서 | git (`backend/docs/`) | git push |

---

## 2. 백업 주기·보관 정책

### A 등급 (절대 손실 금지)

| 항목 | 주기 | 보관 | 저장 위치 |
|---|---|---|---|
| MySQL full snapshot | **매일 02:30 KST** (정합성 검증 02:00 이후) | 최근 7일 + 매주 1회 90일 | AWS S3 (서버 사이드 암호화) |
| MySQL binlog (point-in-time recovery) | **연속 스트리밍** | 최근 7일 (full snapshot 회수일과 일치) | S3 (binlog 로테이션 + 인덱스) |
| `audit_logs` 전용 export (CSV) | **매월 1일** | **3년** | S3 + Glacier (저비용 콜드) |
| 시크릿 매니저 secrets | AWS Secrets Manager 자동 (30일 자동 보존) | KMS 키 분리 | AWS Secrets Manager |

### B 등급

| 항목 | 주기 | 보관 | 비고 |
|---|---|---|---|
| MySQL full snapshot (A 등급과 동일 백업에 포함) | 매일 02:30 | 7일 | A 등급 같이 처리 |
| 집계 테이블 (재계산 가능) | 백업 없음 — `force=true` 재집계로 복구 | — | 원장이 살아 있으면 충분 |

### C 등급

- Redis: 백업 없음 — `redis-cli BGSAVE` 권장하나 운영 정책상 손실 허용
- Kafka: 토픽 retention 3일 — 컨슈머 그룹 오프셋만 ZK/Kafka 자체 관리
- 운영 로그: Loki/ELK 자체 retention 정책

### D 등급

- 모두 git push — 별도 백업 절차 없음
- `private` 저장소 미러링은 GitLab 자체 백업에 의존

---

## 3. 복구 우선순위

장애 발생 시 다음 순서로 복구:

| 순위 | 항목 | 이유 |
|---|---|---|
| 1 | 시크릿 매니저 | DB 접속 자격 증명 없으면 다른 복구 불가 |
| 2 | MySQL (A 등급 우선) | 원장 + 인증 + 감사 로그 |
| 3 | Redis | 인증 블랙리스트·세션 (없어도 동작 가능하나 보안 영향) |
| 4 | Kafka | 컨슈머 그룹 오프셋 — 메시지 손실 허용 시 처음부터 재시작 |
| 5 | 애플리케이션 컨테이너 (Jib 이미지) | git → CI → 재배포 |
| 6 | 집계 테이블 (`force=true` 재집계) | 원장 복구 후 후속 |
| 7 | 정합성 검증 결과 (재배치 호출) | 집계 후 검증 |

### 무엇을 먼저 복구하면 안 되는가

- 애플리케이션 컨테이너를 DB 복구 전에 띄우지 않는다 — Hikari pool 이 영구 실패 상태로 잠김
- 집계 테이블을 원장 복구 전에 직접 시드하지 않는다 — 단일 진실 공급원 원칙 위반
- 시크릿 매니저 복구 없이 root 자격 증명으로 DB 직접 접근 — 감사 로그 누락 위험

---

## 4. 장애 상황별 복구 절차

### 4.1 시나리오 R-01 — MySQL 컨테이너 다운 (단일 인스턴스)

**전제**: 단일 인스턴스 운영 (MVP), HA 미구성

**절차**:
1. MySQL 컨테이너 상태 확인 (`docker ps -a`)
2. 컨테이너 OOM 또는 디스크 가득 차면 원인 파악 (메모리/디스크 확보)
3. `docker compose -f docker-compose.dev.yml up -d mysql` 재시작
4. 헬스체크: `mysqladmin ping` + Spring Boot `actuator/health` UP 확인
5. 큐 적체 메시지 처리 대기 (Kafka 컨슈머 자동 재연결)

**복구 시간 목표**: < 10분 (단순 재시작) / < 1시간 (binlog 적용 필요 시)

### 4.2 시나리오 R-02 — MySQL 디스크 손상 (전체 복원)

**전제**: full snapshot + binlog 가용

**절차**:
1. 새 MySQL 인스턴스 프로비저닝 (또는 동일 컨테이너 볼륨 교체)
2. 시크릿 매니저에서 root + app_user 자격 증명 회수
3. **단계 A** — 최근 full snapshot 복원 (`mysql -u root -p < snapshot.sql`) — 약 10~30분
4. **단계 B** — binlog 재생 (`mysqlbinlog --start-datetime=...` | `mysql ...`) — 약 5~15분
5. 정합성 검증: `SELECT COUNT(*) FROM notification_logs WHERE created_at >= TIMESTAMP_BEFORE_FAILURE` 로 row 누락 확인
6. 애플리케이션 datasource user → `app_user` 로 전환 (이미 운영 적용된 권한 분리, audit-db-permission-rollout.md)
7. 부수: 집계 테이블 `force=true` 재집계 (HourlyAggregator + DailyRollupService)
8. 정합성 검증 자동 배치 수동 트리거 (`POST /api/v1/reconciliations/campaigns/{id}`)

**복구 시간 목표**: RTO < 1시간 (A 등급)

### 4.3 시나리오 R-03 — Redis 다운

**전제**: 캐시 손실 허용 (C 등급)

**절차**:
1. Redis 컨테이너 재시작
2. 캐시는 자연 재빌드 — 별도 조치 없음
3. **다만**: `auth:blacklist:*` 손실 시 강제 로그아웃 토큰이 다시 유효해질 수 있음 → 보안 영향 명시, 운영자 통보

**복구 시간 목표**: < 5분

**부수 영향**:
- `ApiKeyAuthenticationFilter` 의 레이트 리밋이 일시 fail-open (정책 결정 필요, 03-load-and-failure-scenarios §3.3 FT-03)
- `ProjectPermissionEvaluator` 가 매 호출마다 DB 조회 (캐시 미적용) — latency 영향 미미

### 4.4 시나리오 R-04 — Kafka 다운

**전제**: 토픽 retention 3일 (메시지 손실 허용 안 함, 단 컨슈머 정지 가능)

**절차**:
1. Kafka 컨테이너 재시작
2. 컨슈머 (S14P31B101-177 notification-send-result) 자동 재연결 확인
3. 컨슈머 lag 확인 (`kafka-consumer-groups.sh --describe --group reporting-notification-log-consumer`)
4. **lag > 1000 메시지**: 컨슈머 인스턴스 추가 (수평 확장) — MVP 에서는 단일 컨슈머라 lag 해소까지 대기
5. DLQ 토픽 (`*.DLT`) 적체 확인 — S14P31B101-180 정책

**복구 시간 목표**: < 30분 (재시작) / < 2시간 (lag 해소)

### 4.5 시나리오 R-05 — 시크릿 분실 (시크릿 매니저 자체 손상)

**전제**: 매우 드물지만 가장 치명적 — KMS 키 분실 시 회복 불가

**절차**:
1. AWS Secrets Manager 의 자동 30일 보존본에서 회수 시도
2. 회수 실패 시 모든 환경 재구성:
   - MySQL root + app_user + audit_viewer 비번 재발급 (DBA 작업)
   - JWT 서명 시크릿 재발급 → **모든 사용자 강제 로그아웃**
   - 모든 프로젝트의 Public API Key 재발급 → 고객사 통보
   - FCM/APNS 자격 증명 재발급
3. 신규 시크릿 매니저 키 생성 + KMS 분리 보관 (2명 이상의 키 보유자)

**복구 시간 목표**: < 8시간 (정상) / < 24시간 (재발급 필요 시)

### 4.6 시나리오 R-06 — 애플리케이션 컨테이너 회귀

**전제**: 최근 배포로 인한 회귀 (DB·인프라는 정상)

**절차**:
1. CI 그린 게이트 확인 (DoD: 5/15 Code Freeze 이후 모든 배포는 그린 통과)
2. 이전 git tag 또는 직전 develop merge commit 으로 롤백
3. Jib 재빌드 + 컨테이너 재배포 (CI pipeline `package` 잡)
4. 헬스체크 + smoke test (시나리오 #1 로그인 + #2 이벤트 적재)

**복구 시간 목표**: < 30분 (CI 그린 + Jib 빌드 10~15분)

---

## 5. 책임자·역할 분담

| 컴포넌트 | 1차 책임 | 2차 책임 | 통보 채널 |
|---|---|---|---|
| MySQL | 역할 6 (이재영) | 김동현 (인증/테넌트 영향 시) | Mattermost #db-incident |
| Redis | 김동현 | 이재영 | Mattermost #infra-incident |
| Kafka | 역할 5 (발송 워커) | 김동현 | Mattermost #kafka-incident |
| 시크릿 매니저 | DBA (부재 시 김동현) | 이재영 | 전화 (가장 치명적) |
| 애플리케이션 컨테이너 | DevOps (PR/CI 담당자) | 모든 역할 | Mattermost #release |

> MVP 기준 단일 인스턴스 운영이라 책임 구분은 비교적 단순. 운영 도입 시점에 별도 RACI 매트릭스로 확장.

---

## 6. 복구 리허설 계획

### 6.1 1차 리허설 (Code Freeze 직후, 5/16~17)

**목적**: 본 정책의 절차가 실제로 동작하는지 검증

**범위 (R-01, R-02 위주)**:
1. dev 환경에서 의도적 MySQL 컨테이너 stop
2. 5분 후 재시작 → smoke test 통과 확인
3. dev MySQL full dump 생성 → 새 컨테이너 볼륨에 복원 → 시간 측정
4. 복원 후 시나리오 #1 + #2 (인증 + 이벤트 적재) PASS 확인

**성공 판정**:
- ✅ R-01 RTO < 10분 만족
- ✅ R-02 RTO < 1시간 만족 (단, MVP dev 데이터 규모 한정)
- ✅ 복원 후 KPI 정의서 검증값과 카운트 일치 (집계 재실행 후)

### 6.2 2차 리허설 (발표 직전, 5/21)

**목적**: 데모 직전 백업 정상성 점검

**범위**:
1. 최근 dev MySQL snapshot 1건 디스크 잔여량 확인
2. snapshot 무결성 검증 (mysqlcheck 또는 dry-run import)
3. 시크릿 매니저 회수 동작 1회 (실제 사용 안 함, 회수 가능성만)

**성공 판정**: 위 3건 모두 OK

### 6.3 리허설 결과 양식

`docs/operations/recovery-rehearsal-{date}.md` 에 다음 형식으로 기록:

```markdown
## 리허설 #N — {YYYY-MM-DD}

| 시나리오 | RTO 목표 | 실측 | 판정 | 발견 사항 |
|---|---|---|---|---|
| R-01 | < 10분 |   |   |   |
| R-02 | < 1시간 |   |   |   |

**조치**:
-

**다음 리허설 개선점**:
-
```

---

## 7. 운영 시점 점검 항목 (Code Freeze 이후 일일 체크)

- [ ] MySQL full snapshot 최근 24시간 안에 1건 이상 생성 (`aws s3 ls`)
- [ ] binlog 스트리밍 정상 (지연 < 5분)
- [ ] 시크릿 매니저 secrets 모두 존재 (DB 비번/JWT/API Key)
- [ ] Redis 메모리 사용량 < 80%
- [ ] Kafka 컨슈머 lag < 1000
- [ ] CI pipeline 직전 24시간 안에 그린 1회 이상
- [ ] `docker stats` peak CPU < 80% 평균

---

## 8. MVP 범위 외 (Phase 2 후보)

| 항목 | 사유 |
|---|---|
| MySQL HA (Primary/Replica) | MVP 단일 인스턴스 |
| Multi-region 백업 (재해 복구) | 비용 |
| 자동 페일오버 | 단일 인스턴스라 비적용 |
| Redis Sentinel / Cluster | 캐시 손실 허용 |
| Kafka 다중 브로커 | 단일 브로커로 충분 |
| 무중단 마이그레이션 (online schema change) | 마이그레이션 시점 점검 절차로 갈음 |

이 항목들은 5/22 발표 후 "Phase 2 운영 강화" 백로그로 분리.

---

## 9. 참고

- [log-policy.md](./log-policy.md) — 운영/감사 로그 분리 + 보존 정책
- [audit-db-permission-rollout.md](./audit-db-permission-rollout.md) — DB 권한 분리 (app_user / audit_viewer)
- [kpi-and-attribution.md](./kpi-and-attribution.md) — 복구 후 검증 기준 (집계 카운트 일치)
- [ADR-0008](../adr/0008-audit-log-architecture-v2.md) — Append-only 3중 방어선
- [ADR-0017](../adr/0017-public-api-key-authentication.md) — API Key 해시 저장
- [03-load-and-failure-scenarios.md](../qa/03-load-and-failure-scenarios.md) — 장애 시나리오 (R-* 와 일부 겹침)
