# scripts/qa/seed/ — 부하 테스트 시드 데이터

> Owner: 이재영
> 관련 Jira: `S14P31B101-68`
> 관련 문서: [../k6/README.md](../k6/README.md), [backend/docs/qa/03-load-and-failure-scenarios.md](../../../backend/docs/qa/03-load-and-failure-scenarios.md)

본 디렉토리는 LT-01/02/03 시나리오 실행 전에 dev MySQL 에 적재할 시드 SQL 을 제공한다.

---

## 1. 적재 방법

### 사전 조건

- dev MySQL 컨테이너 기동 (`docker-compose up mysql`)
- 빈 `butterfly` DB 또는 기존 dev 데이터에 본 시드 데이터를 덮어쓸 의도

### 실행

```bash
# Windows / Mac / Linux 공통
mysql -h localhost -P 3307 -u root -p butterfly < scripts/qa/seed/seed-load-test-fixtures.sql

# 또는 docker exec 로
docker exec -i $(docker ps --filter name=mysql -q) \
    mysql -u root -p${DB_PASSWORD} butterfly < scripts/qa/seed/seed-load-test-fixtures.sql
```

### 결과 확인

스크립트 마지막에 검증 SELECT 가 출력된다:

```
+-----------------------------------------------+-----------+
| table_name                                    | row_count |
+-----------------------------------------------+-----------+
| projects                                      |         1 |
| campaigns                                     |         5 |
| notification_logs (total)                     |     30001 |
| notification_logs (bucket 2026-05-14T00:00)   |     30000 |
+-----------------------------------------------+-----------+
```

그리고 API Key 평문이 마지막 줄에 출력된다:

```
+----------------------+----------------------------------+-------------------+
| variable             | value                            | db_stored_hash    |
+----------------------+----------------------------------+-------------------+
| API_KEY (k6 환경변수) | pk_live_loadtest000000000000abcd | <sha256-64자>     |
+----------------------+----------------------------------+-------------------+
```

이 `value` 를 k6 실행 시 `-e API_KEY=...` 로 주입.

---

## 2. 적재되는 데이터

| 테이블 | row 수 | 용도 |
|---|---|---|
| `projects` | 1 (id=1) | LT-01/02 의 Project A |
| `members` | 1 (id=1) | OPERATOR 자격 증명 (시연용) |
| `campaigns` | 5 (id=1~5) | LT-03 의 campaign × channel 조합 |
| `notification_logs` (기본) | 1 (logId=42) | LT-01/02 reaction 이벤트 부모 |
| `notification_logs` (30K) | 30000 | LT-03 의 hourly 집계 대상 |

### 30K row 분포 (LT-03 용)

- bucket: **2026-05-14T00:00:00 ~ 01:00:00** (1시간 안)
- 5 campaign × 2 channel × 3000 row = 30K
- status 분포: DELIVERED 80%, PENDING 10%, FAILED 10% (MOD 10 기준)
- 시간 균등 분포 (n × 1.2초 간격)

---

## 3. 주의

### 운영 DB 보호

본 스크립트는 **dev/local 환경에서만 실행**. 스크립트 시작부에 안전 가드 SELECT 가 있으나, 운영 DB 에 의도적으로 실행 시도 시 차단되지 않을 수 있다 — 운영 DB host 접근 자체를 차단할 것 (방화벽 + IAM).

### 멱등성

`ON DUPLICATE KEY UPDATE` 로 같은 스크립트를 여러 번 실행해도 같은 상태로 수렴 (단, 30K notification_logs 의 PK 인 `log_id` 범위가 고정되어 있으므로 PK 충돌 없이 UPDATE 만 발생).

### 정리

부하 테스트 종료 후 데이터 정리 필요 시:

```sql
DELETE FROM user_action_logs WHERE project_id = 1;
DELETE FROM notification_logs WHERE project_id = 1;
DELETE FROM send_reconciliations WHERE project_id = 1;
DELETE FROM campaign_stats_hourly WHERE project_id = 1;
DELETE FROM campaign_stats_daily WHERE project_id = 1;
-- 필요 시 campaigns / members / projects 도 정리 (FK 의존)
```

---

## 4. 스키마 변동 시 갱신

다음 스키마 변동이 있으면 본 SQL 도 갱신:

- `projects` 컬럼 추가/제거
- `campaigns` lifecycle 도메인 스키마 변경
- `notification_logs` 컬럼 추가/제거
- `members.role_type` enum 변경

→ Flyway 마이그레이션 머지 PR 에 본 SQL 갱신 권고 사항 추가.

---

## 5. Phase 2 후보

- 1일치 (240K row) 대규모 시드 — LT-03 의 daily 4종 집계 부하 측정용
- user_action_logs 시드 — OPEN/CLICK/CONVERT 분포 (KPI 비율 정확도 검증)
- 다중 project (multi-tenant 격리 검증) — Project A/B 각각 시드
- failed 분포 다양화 (error_code TOP N 검증)
