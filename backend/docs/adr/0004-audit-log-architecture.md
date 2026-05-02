---
status: accepted
date: 2026-04-21
decision-makers: 역할 1 (인증/테넌트/프로젝트/권한)
consulted: 역할 6 (통합 검증), 전 도메인 (감사 액션 제공자)
---

# ADR-0004: 감사 로그 아키텍처

## Context and Problem Statement

requirements 4-6과 [00-common.md](../api/00-common.md)는 다음 액션에 감사 로그 기록을 필수로 명시한다:

- API 키 재발급
- FCM 자격증명 등록/수정/조회
- 멤버 역할 변경
- 캠페인 즉시 발송 / 예약 발송 / 발송 취소

그러나 초안 ERD에 `audit_logs` 테이블 자체가 없고, 로그 적재 주체(AOP vs 명시적 호출), `action` 값 관리 방식,
`metadata` 구조 등 구현 기준이 정해지지 않아 각 도메인이 착수할 수 없다.

## Decision Drivers

- requirements 4-6 / 00-common.md 규정 만족
- 도메인 추가 시 DB 마이그레이션 부담 최소화
- 민감값(API secret, FCM credentials 원문 등) 평문 기록 절대 금지 (requirements 4-3)
- 디버깅 가능성 — "누가 언제 무엇을 바꿨는지"가 한 줄로 추적 가능해야 함
- MVP 일정 내 구현 가능

## Decision Outcome

### 테이블 스키마

```sql
CREATE TABLE `audit_logs` (
  `audit_id`    BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT       NULL     COMMENT '전역 액션(플랜 변경 등)은 NULL 허용',
  `actor_id`    BIGINT       NOT NULL COMMENT '행위자 member_id',
  `action`      VARCHAR(100) NOT NULL COMMENT '액션 코드. 애플리케이션 상수로 관리',
  `target_type` VARCHAR(50)  NULL     COMMENT '예: CAMPAIGN, MEMBER, CREDENTIAL',
  `target_id`   VARCHAR(100) NULL     COMMENT '복합키/외부 식별자 대응 위해 VARCHAR',
  `metadata`    JSON         NULL     COMMENT 'before/after diff 또는 접근 필드 목록',
  `created_at`  DATETIME     NOT NULL,
  PRIMARY KEY (`audit_id`),
  INDEX `idx_audit_project_created` (`project_id`, `created_at`),
  INDEX `idx_audit_actor_created` (`actor_id`, `created_at`),
  INDEX `idx_audit_action_created` (`action`, `created_at`)
);
```

설계 근거:

- **`project_id` NULL 허용** — 플랜 변경, 테넌트 레벨 액션 등 프로젝트 범위 밖 감사 이벤트 지원
- **`target_id` VARCHAR** — BIGINT 키 외에도 복합키(예: `project_id:credential_id`)나 외부 ID 대응
- **`action` 인덱스 별도** — "어떤 종류 액션만 모아 보기" 운영 시나리오 대응

### `action` 값 체계 — VARCHAR + 애플리케이션 상수

DB `ENUM('...')`이 아니라 `VARCHAR(100)`으로 두고, Java 측 상수 클래스에서 관리한다.

**이유**:

- 도메인 팀이 새 액션을 추가할 때 DB `ALTER TABLE` 불필요
- `ALTER TABLE ... MODIFY COLUMN ENUM(...)` 은 대용량 테이블에서 테이블 락 위험
- MVP 확장성 유리

**구현 위치**: `butterfly-auth` 의 `com.b101.butterfly.auth.audit.AuditAction` 상수 클래스에 전 도메인 action을 모은다.
각 도메인은 해당 상수를 import하여 `AuditLogService` 호출 시 전달.

예:

```java
public final class AuditAction {
    // Auth
    public static final String API_KEY_REGENERATE   = "API_KEY_REGENERATE";
    public static final String FCM_CREDENTIAL_SET   = "FCM_CREDENTIAL_SET";
    public static final String FCM_CREDENTIAL_READ  = "FCM_CREDENTIAL_READ";
    public static final String MEMBER_INVITE        = "MEMBER_INVITE";
    public static final String MEMBER_REMOVE        = "MEMBER_REMOVE";
    public static final String MEMBER_ROLE_CHANGE   = "MEMBER_ROLE_CHANGE";

    // Campaign
    public static final String CAMPAIGN_SEND_NOW    = "CAMPAIGN_SEND_NOW";
    public static final String CAMPAIGN_SCHEDULE    = "CAMPAIGN_SCHEDULE";
    public static final String CAMPAIGN_CANCEL      = "CAMPAIGN_CANCEL";

    private AuditAction() {}
}
```

### `metadata` 구조 — before/after diff

변경성 액션(등록/수정/역할 변경 등)은 before/after 쌍을 JSON으로 기록한다:

```json
{
  "before": { "role": "EDITOR" },
  "after":  { "role": "ADMIN" }
}
```

조회 액션(FCM 자격증명 조회 등)처럼 before/after가 없는 경우 접근한 필드명만 기록:

```json
{
  "fields": ["encrypted_secret", "is_active"]
}
```

### 민감값 마스킹 규칙 (필수)

`metadata`에 다음 값을 평문으로 저장하면 안 된다:

- `secret_api_key`, `public_api_key` 전체 값 → **앞 6자 + `****` + 뒤 4자** 형태로 마스킹
- FCM service account JSON, 암호화된 credential 본문 → **저장 금지**. `credential_id`만 기록
- 비밀번호, 세션 토큰, JWT — **저장 금지**

### INSERT 주체 — `AuditLogService` 명시적 호출

**채택**: 각 도메인 서비스가 `AuditLogService`를 주입받아 명시적으로 호출.

```java
auditLogService.record(AuditAction.MEMBER_ROLE_CHANGE, targetType, targetId,
    Map.of("before", Map.of("role", oldRole), "after", Map.of("role", newRole)));
```

**AOP 미채택 이유**:

- 액션별 `metadata` 구조가 전부 달라 범용 처리 불가 (before/after 추출 규칙을 애너테이션 파라미터로 넘기면 애너테이션이 비대해짐)
- 실패 시 원인 추적이 어려움 (AOP 체인 안에서 예외 발생 시 디버거 진입 불편)
- MVP 범위에서 AOP 인프라 학습/유지 비용이 이득보다 크다

**구현 위치**: `butterfly-auth` 모듈에 인터페이스 정의, 각 도메인 모듈이 implementation 의존으로 주입.

```
butterfly-auth (AuditLogService 인터페이스 + 기본 구현)
  ↑
  ├── butterfly-event
  ├── butterfly-campaign
  └── butterfly-reporting (감사 로그 조회 API 담당)
```

### 조회 API — MVP 범위

| 기능 | MVP | Post-MVP |
|------|:---:|:---:|
| 프로젝트별 감사 로그 목록 | ✅ | |
| cursor 기반 페이지네이션 | ✅ | |
| `action` / 기간 필터 | ✅ | |
| CSV 내보내기 | ❌ | ✅ |
| 테넌트 통합 조회 | ❌ | ✅ |

- cursor 기반을 선택한 이유: `created_at + audit_id` 복합 커서로 대규모 테이블에서도 OFFSET 비효율 방지
- 응답 권한: `ADMIN` 이상

## Consequences

### 좋은 점

- 도메인 확장 시 DB 마이그레이션 0회 (`AuditAction` 상수 추가만)
- before/after 구조로 "무엇이 어떻게 바뀌었는지" 한눈에 파악 가능
- `butterfly-auth`에 인터페이스 집중 — 테스트/Mock 용이

### 나쁜 점 / 주의

- 명시적 호출이라 **개발자가 호출을 누락할 위험** — 감사 대상 API 목록(00-common.md)을 기준으로 코드 리뷰 체크리스트 필수
- `metadata` JSON 구조가 액션마다 다르다 → 조회 UI는 범용 JSON 뷰어 또는 액션별 전용 렌더러 필요
- 민감값 마스킹이 호출자 책임 — `AuditLogService` 자체에 **기본 마스킹 유틸**을 함께 제공해 실수를 줄인다

### Post-MVP 개선 포인트

- 감사 로그 자체도 Kafka로 흘려 **별도 저장소**로 분리 (대용량 대응)
- `target_type` × `action` × `metadata` 스키마를 JSON Schema로 문서화

## More Information

- 관련 ADR: [ADR-0005 인증/인가 필터 배치](0005-auth-filter-placement.md)
- 제안서 §2-2: [backend/docs/proposals/01-erd-api-review.md](../proposals/01-erd-api-review.md)
- requirements 4-3, 4-6
- 감사 대상 목록: [backend/docs/api/00-common.md](../api/00-common.md)
