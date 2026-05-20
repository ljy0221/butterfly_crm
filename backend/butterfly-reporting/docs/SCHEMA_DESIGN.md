# Butterfly CRM - 분석/감사/로깅 파트 설계 문서

> **Owner**: 이재영
> **Version**: v2.0 (2026-04-28)
> **Schema file**: `schema_analytics_audit.sql`
> **Scope**: 분석(Analytics) / 감사(Audit) / 로깅(Logging) / 집계(Aggregation)

---

## 📌 이 문서의 목적

Claude Code가 이 파트의 코드를 작성/수정할 때 참고할 **단일 진실의 원천**입니다.

- 테이블의 의도와 책임
- 테이블 간 관계와 데이터 흐름
- 핵심 비즈니스 규칙
- 외부 팀(다른 도메인)과의 인터페이스

---

## 📋 담당 테이블 (10개)

| 계층 | 테이블 | 역할 |
|---|---|---|
| 로깅 | `notification_logs` | 발송 결과 원장 (Kafka 컨슈머가 적재) |
| 로깅 | `user_action_logs` | 사용자 반응 (오픈/클릭/전환/디스미스) |
| 감사 | `audit_logs` | 보안 민감 작업 감사 (append-only) |
| 감사 | `send_reconciliations` | 발송 정합성 검증 결과 |
| 감사 | `system_health_snapshots` | 시스템 헬스 메트릭 스냅샷 |
| 집계 | `campaign_stats_hourly` | 캠페인 시간 단위 집계 |
| 집계 | `campaign_stats_daily` | 캠페인 일 단위 집계 |
| 집계 | `template_stats_daily` | 템플릿 효율 분석 |
| 집계 | `failure_stats_daily` | 실패 원인 분석 |
| 집계 | `project_stats_daily` | 프로젝트 전사 집계 |

---

## 🔗 외부 의존 테이블 (다른 팀 담당)

본 파트는 다음 테이블을 FK로 참조하지만 직접 관리하지 않습니다.

| 테이블 | 담당자 | 비고 |
|---|---|---|
| `projects` | 김동현 | 모든 테이블이 참조 |
| `members` | 김동현 | `audit_logs`만 참조 |
| `users` | 원영서 | 발송/반응 로그 참조 |
| `devices` | 원영서 | 발송/반응 로그 참조 |
| `campaigns` | 이서연 | 발송 로그/집계 참조 |
| `campaign_executions` | 이서연 | 회차별 추적용 |
| `message_templates` | 이서연 | 템플릿 통계 참조 |

---

## 🏗️ 핵심 설계 원칙

### 1. 3계층 분리

```
원장 (Source of Truth)        → notification_logs, user_action_logs
감사 (Security/Compliance)    → audit_logs, send_reconciliations
파생 (Performance Cache)      → *_stats_* 테이블
```

집계 테이블이 깨져도 원장에서 언제든 재계산 가능해야 합니다.

### 2. Append-only 원칙

다음 테이블은 **INSERT만 허용**:
- `audit_logs` — 트리거 + DB 권한으로 이중 방어
- `user_action_logs` — 사용자 반응은 발생 시점 그대로 보존
- `system_health_snapshots` — 메트릭은 시계열 데이터

`notification_logs`는 예외: status 전이를 위한 UPDATE 허용 (PENDING → SENT → DELIVERED).

### 3. 멀티테넌시 격리

모든 로그/집계 테이블은 `project_id`를 직접 보유합니다 (비정규화).
- 이유: 멀티테넌트 보안 격리 + 조회 성능 + 데이터 수명주기 관리

### 4. 멱등성 보장

집계 테이블의 `UNIQUE KEY`는 자연 키 기반:
- `campaign_stats_hourly`: `(campaign_id, channel, bucket_hour)`
- `campaign_stats_daily`: `(campaign_id, channel, bucket_date)`
- `template_stats_daily`: `(message_template_id, channel, bucket_date)`
- `failure_stats_daily`: `(project_id, channel, error_code, bucket_date)`
- `project_stats_daily`: `(project_id, bucket_date)`

→ Kafka 재처리/배치 재실행 시 중복 적재 방지. **항상 UPSERT로 작성**.

---

## 🔄 데이터 흐름

```
[발송 워커 - 구동엽 팀]
        │
   Kafka: notification-send-result
        │
        ▼
┌─────────────────────────┐
│  notification_logs      │ ◀── status 전이를 위해 UPDATE 허용
└─────────────────────────┘
        │
        │ (엔드유저가 푸시 오픈/클릭)
        ▼
┌─────────────────────────┐
│  user_action_logs       │ ◀── INSERT only
└─────────────────────────┘
        │
        ├─ 5분 스트림 ───▶ campaign_stats_hourly
        │
        └─ 매일 새벽 배치 ──▶ campaign_stats_daily
                              template_stats_daily
                              failure_stats_daily
                              project_stats_daily
                              send_reconciliations

[다른 팀의 민감 작업] ─────▶ audit_logs (append-only)

[인프라 메트릭] ───────────▶ system_health_snapshots
```

---

## 📖 테이블 상세 명세

### `notification_logs` — 발송 결과 원장

**역할**: 모든 채널의 발송 1건 = 1 row. 발송 워커가 Kafka로 발행한 결과를 컨슈머가 적재.

**상태 전이**:
```
PENDING → SENT → DELIVERED   (정상 흐름)
PENDING → FAILED              (실패)
```

**중요**: `CLICKED` 상태는 더 이상 사용 안 함. 클릭은 `user_action_logs.action_type='CLICK'`으로 분리.

**핵심 컬럼**:
- `campaign_execution_id`: 반복 캠페인의 회차 추적용. 단발 캠페인은 NULL 가능.
- `message_template_id`: 템플릿 효율 분석을 위해 발송 시점 템플릿 기록.
- `error_code`: 실패 원인 분석용. 채널별 표준 코드 사용 (INVALID_TOKEN, AUTH_ERROR, RATE_LIMIT, TIMEOUT 등).

**쓰기 패턴**: Kafka 컨슈머가 `messageKey` 기반 멱등성으로 INSERT.

---

### `user_action_logs` — 사용자 반응 로그

**역할**: 한 발송에 대한 사용자의 모든 반응을 추적.

**관계**: `notification_logs` : `user_action_logs` = 1 : N
- 한 발송에 OPEN 1번 + CLICK 2번 + CONVERT 1번 같은 다회 반응 가능

**비정규화**: `project_id`, `campaign_id`를 `notification_logs`에서 복사해서 보유.
- 이유: 집계 쿼리 성능. 매번 JOIN 안 해도 되도록.
- **쓰기 시 반드시 동기화 필요** (애플리케이션 책임)

**쓰기 패턴**: SDK가 `/api/v1/events/actions` API 호출 → INSERT only.

---

### `audit_logs` — 감사 로그

**역할**: 보안 민감 작업 추적. FCM Private Key 변경, API Key 재발급, 멤버 권한 변경, 프로젝트 삭제 등.

**append-only 보장**:
1. 트리거로 UPDATE/DELETE 차단 (실수 방지)
2. DB 권한으로 INSERT/SELECT만 부여 (실질 방어선)
3. 애플리케이션 코드에서 setter 미제공 (`@Immutable`)

**`project_id` NULL 허용**: 시스템 레벨 작업 (예: ADMIN의 신청서 승인)은 특정 프로젝트에 속하지 않음.

**`detail` 컬럼 (JSON)**: 변경 전/후 값. **단, 비밀값 자체는 저장 금지** — 해시만.
```json
{
  "before": { "fcmKeyHash": "a1b2c3..." },
  "after":  { "fcmKeyHash": "x9y8z7..." },
  "reason": "만료된 키 교체"
}
```

**보관 정책**: 월 단위 RANGE 파티셔닝 권장. 1~3년 보관 후 아카이브.

---

### `send_reconciliations` — 발송 정합성 검증

**역할**: "캠페인 회차의 세그먼트 대상자 수 vs 실제 발송된 수" 일치 여부 확인.

**쓰기 시점**: 매일 새벽 배치 또는 수동 트리거 (관리자 API).

**핵심 쿼리**:
```sql
-- 불일치 캠페인 찾기
SELECT * FROM send_reconciliations
WHERE is_consistent = FALSE
ORDER BY checked_at DESC;
```

**회차 단위 검증**: `campaign_execution_id` 기준. 반복 캠페인 47회차에서 누락이 있으면 그 회차만 불일치로 표시.

---

### `system_health_snapshots` — 시스템 헬스

**역할**: Kafka lag, worker TPS, API 에러율 등 운영 지표.

**위치**: 본격 메트릭은 Prometheus/Grafana가 처리. 이 테이블은 **CRM 운영 대시보드용 요약치**만.

**FK 없음**: 외부 의존성 없는 독립 테이블.

---

### 집계 테이블 5종

#### `campaign_stats_hourly`
- **집계 주기**: 5분마다 스트림 워커가 UPSERT
- **버킷**: 시간 단위 (예: `2026-04-28 15:00:00`)
- **용도**: 실시간 대시보드, 시간대별 추이

#### `campaign_stats_daily`
- **집계 주기**: 매일 새벽 1시 배치
- **소스**: hourly 데이터 롤업 + 파생 지표 계산 (open_rate 등)
- **용도**: 캠페인 성과 리포트 API

#### `template_stats_daily`
- **용도**: 템플릿 A/B 비교, "가장 효과적인 제목" 분석
- **소스**: `notification_logs.message_template_id` + `user_action_logs`

#### `failure_stats_daily`
- **용도**: 실패 원인 TOP 5 대시보드
- **소스**: `notification_logs WHERE status='FAILED'` 그룹 바이 error_code

#### `project_stats_daily`
- **용도**: 프로젝트 메인 대시보드 (활성 사용자, 전체 발송, 푸시 오픈률 등)
- **소스**: 모든 채널 합산

---

## 🔑 핵심 비즈니스 규칙

### Rule 1: 멱등성

집계 테이블의 모든 쓰기는 UPSERT. INSERT 후 UPDATE가 아닌 `INSERT ... ON DUPLICATE KEY UPDATE` 사용.

```sql
INSERT INTO campaign_stats_hourly
    (project_id, campaign_id, channel, bucket_hour, sent_count, ...)
VALUES (?, ?, ?, ?, ?, ...)
ON DUPLICATE KEY UPDATE
    sent_count = VALUES(sent_count),
    updated_at = CURRENT_TIMESTAMP;
```

### Rule 2: 비정규화 동기화

`user_action_logs.project_id`, `campaign_id`는 `notification_logs`에서 복사해서 INSERT.

```sql
INSERT INTO user_action_logs (log_id, project_id, campaign_id, ...)
SELECT ?, nl.project_id, nl.campaign_id, ...
FROM notification_logs nl WHERE nl.log_id = ?;
```

### Rule 3: Soft Delete 정책

캠페인이 soft delete되어도:
- `notification_logs`, `user_action_logs`는 **유지** (과거 발송 이력 보존)
- 모든 `*_stats_*`도 **유지** (과거 리포트 조회 가능)
- `send_reconciliations`도 **유지**

→ 캠페인의 `is_deleted=true` 플래그만 바뀌고 파생 데이터는 보존.

### Rule 4: 발송률 계산 공식

```
delivery_rate = delivered_count / sent_count * 100
open_rate     = open_count / delivered_count * 100
click_rate    = click_count / open_count * 100
convert_rate  = convert_count / sent_count * 100
```

분모가 0인 경우 NULL 처리.

### Rule 5: 에러 코드 표준

채널별로 다른 에러를 정규화해서 저장:
- `INVALID_TOKEN` — 푸시 토큰 무효
- `AUTH_ERROR` — 자격 증명 오류 (FCM Key 등)
- `RATE_LIMIT` — 한도 초과
- `TIMEOUT` — 응답 타임아웃
- `INVALID_RECIPIENT` — 잘못된 이메일/전화번호
- `UNKNOWN` — 분류 불가

---

## 🚨 외부 팀과의 인터페이스

### 이서연 팀 (캠페인) → 본 팀

발송 워커가 Kafka로 보내야 하는 메시지에 다음 필드 포함:

```json
{
  "messageKey": "kafka-msg-abc123",
  "projectId": 123,
  "campaignId": 4521,
  "campaignExecutionId": 88123,
  "messageTemplateId": 89,
  "userId": 78901,
  "deviceId": 23456,
  "channel": "PUSH",
  "status": "SENT",
  "errorCode": null,
  "sentAt": "2026-04-28T10:00:05.123Z"
}
```

**중요**: `campaignExecutionId`, `messageTemplateId`가 빠지면 통계 분석 불가능.

### 원영서 팀 (이벤트) → 본 팀

엔드유저 반응 이벤트는 본 팀의 `/api/v1/events/actions` API로 전송:

```json
{
  "logId": 98234521,
  "userId": 78901,
  "deviceId": 23456,
  "actionType": "CLICK",
  "actionTarget": "https://shop.example.com/promo",
  "actionAt": "2026-04-28T10:02:32.000Z"
}
```

### 본 팀 → 다른 팀 (감사 로그 기록)

다른 도메인이 민감 작업을 할 때 본 팀의 `audit_logs`에 INSERT:

```java
auditLogService.record(
    projectId, memberId,
    "FCM_KEY_UPDATE",
    "PROJECT", projectId,
    detail, ipAddress, userAgent
);
```

---

## 📊 주요 조회 패턴 (인덱스 근거)

### Q1. 캠페인 대시보드 시간별 추이
```sql
SELECT * FROM campaign_stats_hourly
WHERE campaign_id = ? AND channel = ?
  AND bucket_hour BETWEEN ? AND ?
ORDER BY bucket_hour;
```
→ `uk_campaign_stats_hourly_nat` 사용

### Q2. 프로젝트 일별 통계
```sql
SELECT * FROM project_stats_daily
WHERE project_id = ?
  AND bucket_date BETWEEN ? AND ?
ORDER BY bucket_date DESC;
```
→ `uk_project_stats_daily_nat` 사용

### Q3. 실패 원인 TOP 5
```sql
SELECT error_code, failed_count
FROM failure_stats_daily
WHERE project_id = ? AND bucket_date = ?
ORDER BY failed_count DESC
LIMIT 5;
```
→ `idx_failure_stats_daily_top` 사용

### Q4. 정합성 불일치 캠페인 조회
```sql
SELECT * FROM send_reconciliations
WHERE is_consistent = FALSE
  AND checked_at >= ?
ORDER BY checked_at DESC;
```
→ `idx_send_reconciliations_inconsistent` 사용

### Q5. 특정 회원의 감사 이력
```sql
SELECT * FROM audit_logs
WHERE member_id = ?
ORDER BY created_at DESC
LIMIT 50;
```
→ `idx_audit_logs_actor` 사용

### Q6. 특정 발송의 사용자 반응
```sql
SELECT * FROM user_action_logs
WHERE log_id = ?
ORDER BY action_at;
```
→ `idx_user_action_logs_log` 사용

---

## 🛠️ 개발 시 체크리스트

### 새로운 집계 추가 시
- [ ] UNIQUE KEY로 자연 키 정의했는가?
- [ ] UPSERT 쿼리로 작성했는가?
- [ ] 배치 재실행 시 중복 발생하지 않는가?
- [ ] `project_id` 필터 인덱스 있는가?

### 로그 INSERT 시
- [ ] 비정규화 컬럼(`project_id`, `campaign_id`)을 정확히 복사했는가?
- [ ] `user_action_logs`라면 부모 `notification_logs.log_id`가 존재하는가?
- [ ] 멱등성 키 처리됐는가? (Kafka 재처리 대비)

### audit_logs INSERT 시
- [ ] 비밀값(API Key, 비밀번호) 자체를 저장하지 않고 해시만 기록했는가?
- [ ] `ip_address`, `user_agent` 채웠는가?
- [ ] `action`, `target_type`을 표준값으로 사용했는가?

### 통계 조회 API 작성 시
- [ ] 호출자가 해당 `project_id` 멤버인지 권한 검증했는가?
- [ ] 날짜 범위가 90일 이내로 제한됐는가? (대량 스캔 방지)
- [ ] 집계 테이블만 조회하고 원장 테이블 직접 안 건드리는가?

---

## 📁 관련 파일

- `schema_analytics_audit.sql` — DDL 통합본
- `butterfly_api_spec_v2.md` — API 명세서 (30개 엔드포인트)

---

## 🔄 변경 이력

### v2.0 (2026-04-28)
- `notification_logs`에 `campaign_execution_id`, `message_template_id` 추가
- `notification_logs.status`에서 `CLICKED` 제거 (user_action_logs로 분리)
- `audit_logs`에 `ip_address` 복구
- `send_reconciliations`에 `campaign_execution_id` 추가 (회차별 검증)
- `template_stats_daily`의 컬럼명 `template_id` → `message_template_id` 통일
- `user_action_logs.device_id` NULL 허용 (이메일/웹 클릭 대응)

### v1.0 (2026-04-24)
- 초기 설계
