---
status: superseded
superseded-by: ADR-0009
date: 2026-04-28
decision-makers: 역할 1 (인증/테넌트/프로젝트/권한), 역할 6 (분석/감사 모듈)
consulted: 전 도메인 (감사 액션 제공자), DBA
supersedes: ADR-0004
---

> **Note (2026-04-29)**: 본 ADR은 [ADR-0009](0009-audit-log-relocation-to-reporting.md)에 의해 superseded됨.
> 스키마(컬럼 구성, append-only 트리거, IP/UA 기록)는 그대로 유지되나, **소유 모듈이 `butterfly-tenant`에서 `butterfly-reporting`으로 이전**되고 발행 경로가 도메인 이벤트로 바뀐다. 자세한 내용은 ADR-0009 참조.

# ADR-0008: 감사 로그 v2 — append-only 3중 방어 + IP/UA 기록

## Context and Problem Statement

[ADR-0004](0004-audit-log-architecture.md)는 v1 단계에서 `audit_logs` 테이블 구조와 `AuditLogService` 호출 규약을 잡았다. 그러나 다음 요구가 추가되면서 스키마와 보안 모델을 갱신할 필요가 생겼다:

1. **요청 출처 추적** — 보안 사고 분석 시 "누가 어디서 했는지"가 중요하므로 IP / User-Agent를 함께 기록해야 한다는 보안팀 요구
2. **append-only 보장 강화** — v1은 텍스트 규약만 있었지만, 실수로 `UPDATE audit_logs SET ...` 실행 시 막을 방법이 없었음. 트리거 + DB 권한 분리로 **이중 방어선** 필요
3. **CSV 내보내기 MVP 진입** — v1에서는 Post-MVP였으나 운영팀의 외부 감사 제출 요구로 MVP 범위로 이동 (`S14P31B101-35`)
4. **컬럼명 표준화** — `actor_id` → `member_id`, `metadata` → `detail`, `target_id`는 BIGINT로 단순화

또한 v1의 `target_id VARCHAR(100)` 결정은 실제로 사용되는 모든 케이스가 BIGINT 단일키로 표현 가능했다(복합키 케이스 없음). VARCHAR 유지 비용이 이득보다 컸다.

## Decision Drivers

- 보안 사고 분석 시 IP / User-Agent 기반 추적 가능성
- "실수로 INSERT 외 작업이 실행되어도 차단" — 세 단계 방어선
- 비밀값(API Key, FCM credential, 비밀번호) 평문 저장 절대 금지 — v1 정책 유지
- 외부 감사 제출용 CSV 반출 가능 (v1에서는 Post-MVP였으나 MVP로 이동)
- v1과의 마이그레이션은 별도 컬럼 추가만으로 가능 (테이블 재생성 회피)

## Decision Outcome

DDL 단일 진실의 원천은 [`backend/butterfly-analytics/db/schema_analytics_audit.sql`](../../butterfly-analytics/db/schema_analytics_audit.sql) v2.0이며, 본 ADR은 그 결정 근거를 기록한다.

### 테이블 스키마 (v2)

```sql
CREATE TABLE `audit_logs` (
    `audit_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`   BIGINT       NULL     COMMENT '프로젝트 ID (시스템 레벨 시 NULL)',
    `member_id`    BIGINT       NOT NULL COMMENT '작업 수행 회원 ID',
    `action`       VARCHAR(100) NOT NULL COMMENT '작업 종류 (FCM_KEY_UPDATE 등)',
    `target_type`  VARCHAR(50)  NOT NULL COMMENT '대상 엔티티 타입',
    `target_id`    BIGINT       NULL     COMMENT '대상 엔티티 ID',
    `detail`       JSON         NULL     COMMENT '추가 상세 (변경 전/후 값 등)',
    `ip_address`   VARCHAR(45)  NULL     COMMENT '요청 IP (IPv6 대응)',
    `user_agent`   VARCHAR(500) NULL     COMMENT 'User-Agent',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`audit_id`),
    KEY `idx_audit_logs_project_action` (`project_id`, `action`, `created_at`),
    KEY `idx_audit_logs_actor`          (`member_id`, `created_at`),
    KEY `idx_audit_logs_target`         (`target_type`, `target_id`),
    KEY `idx_audit_logs_action_time`    (`action`, `created_at`),
    KEY `idx_audit_logs_created_at`     (`created_at`)
);
```

### v1 → v2 컬럼 변경 매트릭스

| v1 (ADR-0004) | v2 (현재) | 사유 |
|---|---|---|
| `actor_id` | `member_id` | 다른 도메인의 명명 컨벤션과 통일 |
| `target_id VARCHAR(100)` | `target_id BIGINT NULL` | 실제 사용처가 모두 단일 BIGINT 키 — VARCHAR 비용 회피 |
| `metadata JSON` | `detail JSON` | 명사 일관성 (한국어 "상세" 직역) |
| ❌ 없음 | `ip_address VARCHAR(45)` ✨ | IPv6 대응 (16바이트 표현 + 콜론) |
| ❌ 없음 | `user_agent VARCHAR(500)` ✨ | 요청 출처 추적 |

### Append-only 3중 방어선

ADR-0004는 "append-only 정책"만 기술했지만 v2에서는 **세 단계 모두 명시적으로 구현**한다:

#### ① 데이터베이스 트리거 (실수 방지)

```sql
CREATE TRIGGER trg_audit_logs_no_update
BEFORE UPDATE ON audit_logs
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. UPDATE is forbidden.';
END;

CREATE TRIGGER trg_audit_logs_no_delete
BEFORE DELETE ON audit_logs
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. DELETE is forbidden.';
END;
```

→ 개발자가 실수로 UPDATE/DELETE를 날려도 SQLSTATE 45000으로 차단된다.

#### ② DB 권한 분리 (실질 방어선)

```sql
-- 애플리케이션 계정은 INSERT/SELECT만
GRANT INSERT, SELECT ON butterfly.audit_logs TO 'app_user'@'%';

-- 감사 조회 전용 계정 (선택)
GRANT SELECT ON butterfly.audit_logs TO 'audit_viewer'@'%';
```

→ 트리거가 도달하기 전에 GRANT 단계에서 거부된다. 침해 시나리오에서도 root가 아니면 변조 불가.

#### ③ 애플리케이션 레벨 (Hibernate `@Immutable`)

```java
@Entity
@Immutable          // Hibernate가 UPDATE 쿼리 생성 자체 차단
@Table(name = "audit_logs")
public class AuditLog {
    // setter 미제공
}
```

→ ORM 레벨에서도 UPDATE 쿼리가 만들어지지 않는다.

세 방어선을 모두 통과해야만 변조가 가능하므로, 실질적으로 INSERT-only가 강제된다.

### `AuditLogService` v2 시그니처

ADR-0004의 시그니처를 IP/UA 인자로 확장한다:

```java
auditLogService.record(
    Long projectId,           // 시스템 레벨 시 null 허용
    Long memberId,
    String action,            // AuditAction 상수
    String targetType,
    Long targetId,
    Map<String, Object> detail,  // before/after 또는 fields
    String ipAddress,         // HttpServletRequest 에서 추출
    String userAgent
);
```

호출 위치에서 IP/UA를 알아내려면 컨트롤러 레이어에서 호출하거나, `RequestContextHolder` 또는 MDC 패턴으로 서비스에 전달.

### 비밀값 마스킹 규칙 (강화)

v1의 마스킹 규칙을 유지하되, **변경 전/후는 해시만 저장**으로 강제:

```json
{
  "before": { "fcmKeyHash": "a1b2c3..." },
  "after":  { "fcmKeyHash": "x9y8z7..." },
  "reason": "만료된 키 교체"
}
```

- API Key 원문, FCM service account JSON, 비밀번호, 세션 토큰 → **저장 금지**
- 변경 추적이 필요한 비밀값은 **SHA-256 해시 앞 8자**만 기록
- 마스킹 책임은 호출자(도메인 서비스) — `AuditLogService`는 마스킹 유틸 함께 제공

### CSV 내보내기 — MVP 진입

v1에서는 Post-MVP였으나 v2에서는 MVP 범위로 이동 (`S14P31B101-35`):

- 인코딩: UTF-8 with BOM (Excel 호환)
- RFC 4180 이스케이프 (쉼표·따옴표·줄바꿈)
- 대용량(임계 초과) 시 비동기 생성 + 완료 알림
- **CSV 반출 자체가 감사 대상** — 내보내기 실행 시 `audit_logs`에 `action=AUDIT_LOG_EXPORT` 자체 기록

### 보관 정책 (v1에 누락 → v2 신규)

- 월 단위 RANGE 파티셔닝 권장 (`PARTITION BY RANGE (YEAR(created_at)*100 + MONTH(created_at))`)
- 1~3년 보관 후 아카이브 (외부 스토리지 또는 별도 cold storage)
- 보관 기간은 프로젝트 컴플라이언스 요구에 따라 조정 가능

### 인덱스 (v2 — 5종)

ADR-0004의 3종 인덱스에서 2종 추가:

| 인덱스 | 용도 |
|---|---|
| `idx_audit_logs_project_action` | 프로젝트 단위 액션별 필터 + 시간 정렬 (대시보드) |
| `idx_audit_logs_actor` | 특정 회원의 활동 이력 (member 페이지) |
| `idx_audit_logs_target` | 특정 엔티티(예: campaign 4521)에 대한 모든 변경 추적 |
| `idx_audit_logs_action_time` | "최근 FCM_KEY_UPDATE 만 모아 보기" 운영 시나리오 |
| `idx_audit_logs_created_at` | 시계열 전체 조회 / 보관 정책 파티셔닝 보조 |

## Consequences

### 좋은 점

- IP/UA 기록으로 보안 사고 분석 시간 단축
- 3중 방어선으로 실수·침해 모두에 강건
- 컬럼명 표준화로 다른 도메인과의 명명 일관성 확보
- CSV 반출까지 MVP에서 처리 가능

### 나쁜 점 / 주의

- IP/UA 추출은 컨트롤러 레이어에서 가능 — 서비스 레이어에서 호출하는 경우 MDC/RequestContextHolder 의존
- 트리거 + GRANT 적용은 DBA 협의 필요 (운영 환경) — 로컬은 root이므로 트리거만 검증 가능
- 비밀값 해시화 책임이 호출자에 있어 누락 위험 — 코드 리뷰 체크리스트 필수
- v1 → v2 마이그레이션은 컬럼 추가(`ip_address`, `user_agent`) + 컬럼명 변경(`actor_id` → `member_id`, `metadata` → `detail`)으로 진행. 기존 row의 IP/UA는 NULL로 채움

### Post-MVP 개선 포인트

- 감사 로그를 Kafka로 흘려 별도 저장소(예: 전용 PostgreSQL 또는 cold storage)로 분리
- `target_type` × `action` × `detail` 스키마를 JSON Schema로 문서화 (조회 UI 자동 렌더러)
- IP 지오로케이션 추가 (보안 분석용)

## More Information

- 단일 진실의 원천: [`backend/butterfly-analytics/docs/SCHEMA_DESIGN.md`](../../butterfly-analytics/docs/SCHEMA_DESIGN.md)
- DDL: [`backend/butterfly-analytics/db/schema_analytics_audit.sql`](../../butterfly-analytics/db/schema_analytics_audit.sql)
- API 명세: [`backend/butterfly-analytics/docs/butterfly_api_spec_v2.md`](../../butterfly-analytics/docs/butterfly_api_spec_v2.md) (#20, #21)
- 관련 ADR: [ADR-0005](0005-auth-filter-placement.md), [ADR-0007](0007-analytics-aggregation-tables-v2.md)
- 슈퍼시드: [ADR-0004](0004-audit-log-architecture.md)
- 관련 Jira: `S14P31B101-125` (트리거 + 권한 분리), `S14P31B101-35` (감사 로그 조회/CSV)
