<!-- markdownlint-disable MD029 MD024 MD032 MD031 MD060 MD022 -->

# ERD 및 API 명세 리뷰 제안서 (역할 6: 분석/통합 검증)

작성자: 역할 6 (분석 / 대시보드 / 통합 검증)
작성일: 2026-04-21
최종 수정: 2026-04-21 (역할 1·3·4 합의 반영)
대상: 전체 백엔드 팀 (역할 1~6)
상태: 역할 3·4 합의 완료, 역할 1·2·5 합의 필요

## 합의 이력

- **2026-04-21 (역할 3)**: 세그먼트 조건 트리 JSON 방식 확정 (항목 11) → [ADR-0001](../adr/0001-segment-condition-tree-json.md)
- **2026-04-21 (역할 4)**: 캠페인 도메인 전면 재설계 합의 — 캠페인 정의 ↔ 실행 회차 분리 (항목 5 및 연쇄 변경) → [ADR-0002](../adr/0002-campaign-definition-execution-split.md)
  - `campaigns.status` 생애주기 재정의: `DRAFT / ACTIVE / PAUSED / ARCHIVED`
  - `campaign_executions` 테이블 신설 → 발송 상태(SCHEDULED/SENDING/COMPLETED/FAILED/**CANCELED**) 및 스냅샷 보유
  - `campaign_stats` PK를 `execution_id`로 변경 (반복 발송 회차별 1:1)
  - `templates` 테이블 신설
  - `channel_credentials` 테이블 신설
- **2026-04-21 (역할 1)**: 감사 로그 및 인증/인가 필터 확정 (항목 6 및 신규 항목 E) → [ADR-0004](../adr/0004-audit-log-architecture.md), [ADR-0005](../adr/0005-auth-filter-placement.md)
  - `audit_logs` 테이블 스키마 확정: `project_id` NULL 허용, `action` VARCHAR + 앱 상수, `target_id` VARCHAR
  - `metadata` JSON에 before/after diff 저장 (민감값 마스킹 필수)
  - INSERT 주체: `butterfly-auth`의 `AuditLogService`를 각 도메인이 명시적 호출 (AOP 미사용)
  - 감사 로그 조회 API는 MVP 포함 (프로젝트별 목록/필터/cursor 페이지네이션)
  - 공통 인증 필터(JWT 파싱) + 프로젝트 권한 필터(`project_members` 역할 검증) 2종을 `butterfly-auth`가 제공
  - 권한 선언은 `@PreAuthorize` 방식으로 전 도메인 통일 (SpEL + 권한 평가 빈)
  - 역할 조회 캐싱: Redis TTL 5분 + 역할 변경 시 즉시 무효화
- **2026-04-21 (팀 전체)**: 프로젝트 타임존 정책 확정 (항목 8)
  - MVP 타겟 시장이 한국 한정이므로 `projects.timezone` 컬럼 미추가
  - 서버 JVM 기본 타임존을 `Asia/Seoul`로 고정, 집계 `bucket_date`도 동일 기준
  - 해외 확장 시 재논의 (ADR 신설 예정)

---

## 0. 요약 (TL;DR)

현재 [backend/docs/erd.md](../erd.md)와 [backend/docs/api/](../api/)를 통합 검증 시각에서 리뷰한 결과, 다음 이슈를 확인했다.

**Blocker (분석 파이프라인 착수 전 반드시 합의):**

1. 집계 테이블 3종(`campaign_metric_daily`, `channel_metric_daily`, `segment_metric_daily`) 부재 — 현재 ERD로는 `dashboard?from=..&to=..` API 자체가 불가능
2. `notification_logs`에 `project_id` 없음 + 집계용 인덱스 부재 — 멀티테넌시 규칙 위반 + 대시보드 성능 문제
3. 발송 상태 카운트 규칙 미정의 (전이식 vs 누적) — 집계 로직이 정해지지 않음
4. SDK 도달/클릭 콜백 인증 취약 — 분석 데이터 자체를 신뢰할 수 없음

**High (MVP 전 정리):**

5. ✅ ~~`campaigns.status`에 `CANCELED` 없음~~ → **역할 4 합의 완료** ([ADR-0002](../adr/0002-campaign-definition-execution-split.md))
6. ✅ ~~`audit_logs` 테이블 부재~~ → **역할 1 합의 완료** ([ADR-0004](../adr/0004-audit-log-architecture.md))
7. 전환(conversion) 이벤트 스코프 미정 — requirements 3-6은 필수 명시
8. ✅ ~~프로젝트 타임존 정책~~ → **팀 합의 완료 (2026-04-21)**: MVP는 한국 시장 한정이라 `Asia/Seoul`을 서버 레벨에서 고정. `projects.timezone` 컬럼 미추가. 향후 해외 확장 시 재논의.

**Medium (MVP 스코프 안에서 정리):**

9. 다수 테이블 `project_id` 누락 (`segment_rules`, `devices`, `events_category`, `campaign_stats`)
10. FK 제약 3개만 정의됨 → 데이터 무결성 취약
11. ✅ ~~세그먼트 규칙 AND/OR 중첩 스키마 부재~~ → **역할 3 합의 완료** ([ADR-0001](../adr/0001-segment-condition-tree-json.md))
12. `users.project_id` 주석 오류 (tenant_id가 아님)
13. `campaigns.channel` "테이블 분리 필요" 주석 — MVP 이후 계획 명시 필요

**추가 확정 (역할 1 합의):**

E. 공통 인증/인가 필터 배치 — `butterfly-auth` 제공, `@PreAuthorize` 통일 ([ADR-0005](../adr/0005-auth-filter-placement.md))

---

## 1. 분석 도메인 Blocker

### 1-1. 집계 테이블 3종 신설 필요

#### 문제

[06-analytics.md](../api/06-analytics.md) API 5개 중 3개가 기간 필터(`?from=..&to=..`)를 요구한다.
현재 `campaign_stats`는 **캠페인당 1행, 누적 총계**만 가지므로 아래 질의가 불가능:

- 대시보드 `dailyTrend` 배열
- 캠페인 성과 비교의 기간 필터
- 채널별/세그먼트별 기간 성과 비교

requirements 11-6이 명시한 `campaign_metric_daily`, `channel_metric_daily`, `segment_metric_daily`가 ERD에 통째로 빠져 있다.

#### 제안 스키마 (2026-04-21 역할 4 합의 반영 — execution_id 기반)

```sql
CREATE TABLE `campaign_metric_daily` (
  `project_id`       BIGINT     NOT NULL,
  `campaign_id`      BIGINT     NOT NULL,
  `execution_id`     BIGINT     NOT NULL COMMENT '발송 회차 ID (RECURRING 대응)',
  `bucket_date`      DATE       NOT NULL COMMENT '프로젝트 타임존 기준 일자',
  `sent_count`       INT        NOT NULL DEFAULT 0,
  `delivered_count`  INT        NOT NULL DEFAULT 0,
  `clicked_count`    INT        NOT NULL DEFAULT 0,
  `failed_count`     INT        NOT NULL DEFAULT 0,
  `converted_count`  INT        NOT NULL DEFAULT 0,  -- 7번 항목 합의 후 확정
  `updated_at`       DATETIME   NOT NULL,
  PRIMARY KEY (`project_id`, `campaign_id`, `execution_id`, `bucket_date`),
  INDEX `idx_cmd_project_campaign_date` (`project_id`, `campaign_id`, `bucket_date`)
);

CREATE TABLE `channel_metric_daily` (
  `project_id`       BIGINT                 NOT NULL,
  `channel`          ENUM('PUSH','EMAIL')   NOT NULL,
  `bucket_date`      DATE                   NOT NULL,
  `sent_count`       INT                    NOT NULL DEFAULT 0,
  `delivered_count`  INT                    NOT NULL DEFAULT 0,
  `clicked_count`    INT                    NOT NULL DEFAULT 0,
  `failed_count`     INT                    NOT NULL DEFAULT 0,
  `updated_at`       DATETIME               NOT NULL,
  PRIMARY KEY (`project_id`, `channel`, `bucket_date`)
);

CREATE TABLE `segment_metric_daily` (
  `project_id`       BIGINT    NOT NULL,
  `segment_id`       BIGINT    NOT NULL,
  `bucket_date`      DATE      NOT NULL,
  `campaign_count`   INT       NOT NULL DEFAULT 0,
  `sent_count`       INT       NOT NULL DEFAULT 0,
  `delivered_count`  INT       NOT NULL DEFAULT 0,
  `clicked_count`    INT       NOT NULL DEFAULT 0,
  `failed_count`     INT       NOT NULL DEFAULT 0,
  `updated_at`       DATETIME  NOT NULL,
  PRIMARY KEY (`project_id`, `segment_id`, `bucket_date`)
);
```

**참고**: `campaign_stats`(역할 4 주관)의 PK도 `execution_id`로 변경되었으므로
API 58 `/campaigns/{campaignId}/stats`의 응답은 **모든 회차 합산(누적)** 으로 돌려주고,
회차별 상세는 신규 API `/campaigns/{campaignId}/executions/{executionId}/stats`로 제공한다.

#### 갱신 전략 (MVP)

1. Worker가 발송 시점/콜백 수신 시점에 해당 일자 버킷에 `UPSERT` (원자적 증분)
2. 안전장치로 1회/시간 재집계 스케줄러 — `notification_logs`에서 원천 복구 (멱등)
3. `campaign_stats`는 **유지하되** `campaign_metric_daily`의 캠페인 합계 뷰로 재정의 or 워커가 함께 갱신 (API 58이 의존)

#### 대안 (기각)

- 매 조회 시 `notification_logs`에서 실시간 집계 → 대시보드 응답 시간 목표(수 초 내외) 위반. requirements 3-6 완료 기준이 "집계 테이블 우선 조회"라 **기각**.

---

### 1-2. `notification_logs`에 `project_id` 추가 + 인덱스 보강

#### 문제

- `notification_logs`에 `project_id` 없음. 현재는 `campaigns`와 JOIN 해야 프로젝트를 알 수 있다.
- 이 테이블은 시스템에서 가장 큰 테이블이 되며, **모든 집계 쿼리가 이 테이블을 스캔**한다.
- 매번 JOIN 경유 + `project_id` 필터가 인덱스를 타지 못하면 대시보드가 서비스 규모 늘자마자 죽는다.
- requirements 4-5 "모든 핵심 도메인 테이블은 `project_id`를 가진다" 위반.

#### 제안

```sql
ALTER TABLE `notification_logs`
  ADD COLUMN `project_id` BIGINT NOT NULL COMMENT '소속 프로젝트 ID' AFTER `log_id`;

-- 집계/조회 핵심 인덱스
CREATE INDEX `idx_nlogs_project_campaign_sent`
  ON `notification_logs` (`project_id`, `campaign_id`, `sent_at`);

CREATE INDEX `idx_nlogs_project_sent`
  ON `notification_logs` (`project_id`, `sent_at`);

CREATE INDEX `idx_nlogs_project_user`
  ON `notification_logs` (`project_id`, `user_id`, `created_at`);
```

- 역할 5(발송)가 로그 적재 시 `project_id`를 함께 채운다.
- 이 테이블은 발송량 ∝ 성장으로 가장 빨리 커지므로 **파티셔닝 후보**(월 단위 `sent_at` 기준). MVP는 단일 테이블이되 인덱스만 확정.

---

### 1-3. 발송 상태 카운트 규칙 확정

#### 문제

- [05-delivery.md](../api/05-delivery.md)의 상태 흐름은 `SENT → DELIVERED → CLICKED` **전이식**
- `campaign_stats.sent_count / delivered_count / clicked_count`가 **개별 카운트인지 누적 카운트인지 미정의**
- 집계 로직이 이 규칙 없이는 한 줄도 못 쓴다.

#### 제안 (권장안)

**타임스탬프 기준 집계**(상태 enum을 카운트 계산에 사용하지 않음):

| 지표 | 집계 정의 |
|------|----------|
| `sent_count` | `COUNT(*) WHERE sent_at IS NOT NULL` |
| `delivered_count` | `COUNT(*) WHERE delivered_at IS NOT NULL` |
| `clicked_count` | `COUNT(*) WHERE clicked_at IS NOT NULL` |
| `failed_count` | `COUNT(*) WHERE status = 'FAILED'` |
| `deliveryRate` | `delivered_count / sent_count * 100` |
| `clickRate` | `clicked_count / delivered_count * 100` (또는 `/ sent_count`) |

- `status` enum은 **현재 상태 표시용**으로 유지하되 카운트 계산에는 쓰지 않음
- `clickRate` 분모 정책도 이번에 확정 필요 (delivered 기준 권장 — 업계 관행)
- Worker가 `delivered`/`clicked` 콜백 처리 시 `UPDATE ... SET delivered_at=..., status='DELIVERED'`로 양쪽 갱신

---

### 1-4. SDK 도달/클릭 콜백 인증 강화

#### 문제

[05-delivery.md](../api/05-delivery.md)의 `POST /sdk/v1/notifications/{logId}/delivered`, `.../clicked`는 **프로젝트 공개 API Key** 인증.
→ 공개 키이므로 공격자가 `logId` 범위를 추측해 클릭/도달 수치를 조작 가능.
→ **분석 대시보드 숫자 자체를 신뢰 불가.**

#### 제안

1. **서명 토큰 방식**: 푸시 페이로드에 `{logId, expiresAt, sig}` 서명 토큰을 실어 보낸다. 콜백은 해당 토큰을 그대로 반환.
   - 서명: `HMAC_SHA256(secret_api_key, logId || expiresAt)`
   - 서버는 검증 후 1회만 수락 (또는 중복 무시)
2. 토큰 부재 시 콜백 거부 (현재 명세 호환성을 위해 deprecated 플래그로 남길 수 있음)
3. 또는 최소 방어로 콜백 결과는 **항상 멱등**(동일 `logId`에 재호출되어도 기존 타임스탬프 유지)

역할 5 주도, 역할 6(분석)은 데이터 신뢰성 관점에서 요구.

---

## 2. ERD 구조 이슈 (High)

### 2-1. 캠페인 도메인 전면 재설계 (✅ 2026-04-21 역할 4 합의 완료)

#### 문제 (원 제안)

- 기존 enum: `DRAFT, SCHEDULED, SENDING, COMPLETED, FAILED`
- 취소 시 `SCHEDULED → DRAFT`로 되돌려 취소/초안 구분 불가

#### 결정 (역할 4 주도)

단순히 `CANCELED` 값을 추가하는 것을 넘어, **캠페인 정의 ↔ 실행 회차 분리**로 해결.

**1) `campaigns` (캠페인 정의)** — 상태는 운영 생애주기만 표현

```sql
status ENUM('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')
```

- `DRAFT`: 필수 설정 미완료 or 임시 저장
- `ACTIVE`: 운영 중 (즉시 발송 후, 또는 예약/반복이 스케줄러에 감시 중)
- `PAUSED`: RECURRING 캠페인을 운영자가 임시 중지
- `ARCHIVED`: 단발/예약 종료 or 리스트 숨김

**2) `campaign_executions` (신설, 실제 발송 회차)** — 워커가 물고 도는 단위

```sql
status ENUM('SCHEDULED','SENDING','COMPLETED','FAILED','CANCELED')
```

- `snapshot_title/body/image_url/data` — 발송 확정 시 템플릿 변수 치환 결과 저장 (과거 재현 보장)
- `idempotency_key UNIQUE` — IMMEDIATE는 클라이언트가 HTTP 헤더 `Idempotency-Key: <UUID>`로 전달, SCHEDULED/RECURRING은 스케줄러가 `camp_{id}_tick_{yyyyMMddHHmm}` 형태로 생성
- `scheduled_at`, `send_started_at`, `send_completed_at`

**3) `campaign_stats` PK 변경**

```sql
PRIMARY KEY (execution_id)   -- 기존: (campaign_id)
```

**4) `templates` (신설, 템플릿 본체)** — requirements 3-4 필수

```sql
template_id  BIGINT PK
project_id   BIGINT FK
channel      ENUM('PUSH')
name         VARCHAR
title_template TEXT
body_template  TEXT
image_url    VARCHAR NULL
```

데이터 흐름:

1. 마케터가 `templates`에 템플릿 미리 작성
2. `campaigns` 생성 시 `template_id` 참조
3. 실제 발송 시각 도래 → `campaign_executions` 생성 시 템플릿 로드 + 변수 치환 → 결과를 `snapshot_*`에 저장

**5) `channel_credentials` (신설)** — 감사 로그 대상 분리

```sql
credential_id    BIGINT PK
project_id       BIGINT FK
channel          ENUM('PUSH','EMAIL')
encrypted_secret VARCHAR(500)
is_active        BOOLEAN
created_at, updated_at
UNIQUE(project_id, channel)
```

- 변경 이력은 `audit_logs`(역할 1)에 위임
- 워커는 `is_active=false` 시 해당 execution을 `FAILED(Credentials Inactive)` 처리

#### 분석 도메인 파급 효과 (내가 반영할 것)

- API 58 `/campaigns/{campaignId}/stats` = **모든 회차 누적 합산**
- 신규 API: `GET /api/projects/{projectId}/campaigns/{campaignId}/executions/{executionId}/stats`
- 집계 테이블의 PK에 `execution_id` 포함 (§1-1 참조)
- 대시보드 "기간 내 발송된 캠페인 수"는 `campaign_executions.send_started_at` 기준으로 카운트
- `total_target` 확정 시점: `SCHEDULED` 상태에서는 NULL, `SENDING` 진입 시 세그먼트 엔진(역할 3) 호출 후 업데이트
  → 프런트는 SCHEDULED 행을 "모수 계산 대기 중"으로 표시

---

### 2-2. `audit_logs` 테이블 신설

#### 문제

- requirements 4-6 + [00-common.md](../api/00-common.md)는 감사 로그를 의무화
- ERD에 테이블 자체가 없음

#### 제안 스키마

```sql
CREATE TABLE `audit_logs` (
  `audit_id`    BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT       NOT NULL,
  `actor_id`    BIGINT       NOT NULL COMMENT '행위자 member_id',
  `action`      VARCHAR(100) NOT NULL COMMENT '예: CAMPAIGN_SEND, API_KEY_REGENERATE',
  `target_type` VARCHAR(50)  NULL     COMMENT '예: CAMPAIGN, MEMBER',
  `target_id`   BIGINT       NULL,
  `metadata`    JSON         NULL     COMMENT '변경 전후, 요청 IP 등',
  `created_at`  DATETIME     NOT NULL,
  PRIMARY KEY (`audit_id`),
  INDEX `idx_audit_project_created` (`project_id`, `created_at`)
);
```

역할 1 주도.

---

### 2-3. 전환(conversion) 이벤트 스코프 확정

#### 문제

- requirements 3-6 필수 항목: "전환 수 집계", "전환 이벤트 기록"
- requirements 11-6 필수 항목: `ConversionEvent`
- 현재 ERD/API 모두 누락
- MVP에서 정말 뺄 거면 requirements를 함께 수정해야 함

#### 제안 (두 가지 중 선택)

**Option A — MVP 포함 (최소)**:
```sql
CREATE TABLE `conversion_events` (
  `conversion_id`     BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`        BIGINT       NOT NULL,
  `user_id`           BIGINT       NOT NULL,
  `campaign_id`       BIGINT       NULL  COMMENT '어트리뷰션된 캠페인',
  `notification_log_id` BIGINT     NULL  COMMENT '클릭 로그 기준',
  `event_name`        VARCHAR(255) NOT NULL COMMENT '전환으로 지정된 이벤트명',
  `event_value`       DECIMAL(12,2) NULL  COMMENT '매출 등 값',
  `converted_at`      DATETIME     NOT NULL,
  PRIMARY KEY (`conversion_id`),
  INDEX `idx_conv_project_campaign` (`project_id`, `campaign_id`, `converted_at`)
);
```
+ 프로젝트별 전환 이벤트명 설정 (예: `purchase_complete`를 전환으로 지정)
+ Worker가 `events` 수집 시 전환명이면 `conversion_events`에 동시 적재

**Option B — MVP 제외**:
- requirements 3-6의 "전환 수 집계", "전환 이벤트 기록" 두 줄 삭제
- analytics API 응답에 `convertedCount` 추가하지 않음
- 중간발표 때 "어트리뷰션은 포함하지 않은 MVP"로 명시

**권장: Option B**. 어트리뷰션 모델 합의만으로도 1주가 소모됨. 팀 역량/일정 고려 시 MVP 제외가 현실적.

---

### 2-4. 타임존 정책 확정 (✅ 2026-04-21 팀 합의: MVP 한국 한정)

#### 문제

- `?from=2026-04-01&to=2026-04-20` 질의가 **어느 타임존 기준 "하루"인지** 명시되지 않음
- 일간 집계 테이블 PK가 `bucket_date`(DATE)인 이상 반드시 tz 기준이 있어야 함

#### 결정

MVP 타겟 시장이 한국으로 좁혀진 상태이므로 **프로젝트별 타임존을 도입하지 않고
서버 레벨에서 `Asia/Seoul`을 고정**한다. `projects.timezone` 컬럼 추가는 **기각**.

- Worker 집계 시 `sent_at`(UTC 저장) → **`Asia/Seoul` 고정 변환** → `DATE(...)`로 버킷팅
- API 파라미터의 ISO date는 **`Asia/Seoul` 기준 하루**로 해석
- 서버 JVM의 기본 타임존은 `Asia/Seoul`로 운영 환경에서 고정 (배포 설정 필수)
- 집계 테이블 `bucket_date` 주석을 "`Asia/Seoul` 기준 일자"로 확정 (ADR-0003 동일 적용)

#### 향후 확장 시 재논의

해외 프로젝트가 추가되면 다음 중 택일:

- `projects.timezone` 컬럼 추가 + 기존 집계 재계산
- 테넌트 단위로 별도 분석 저장소 분리

---

## 3. ERD 구조 이슈 (Medium)

### 3-1. 누락된 `project_id` 컬럼 (멀티테넌시 누수)

requirements 4-5 위반. 아래 테이블에 추가:

| 테이블 | 현재 | 제안 |
|--------|------|------|
| `segment_rules` | `segment_id`만 | `project_id` 추가 (비정규화이지만 조회 성능) |
| `devices` | 없음 | `project_id` 추가 (user_id NULL 허용이므로 필수) |
| `events_category` | 없음 | `project_id` 추가 (프로젝트별 태그 정의 전제이면 필수) |
| `campaign_stats` | 없음 | `project_id` 추가 |

```sql
ALTER TABLE `segment_rules` ADD COLUMN `project_id` BIGINT NOT NULL;
ALTER TABLE `devices`       ADD COLUMN `project_id` BIGINT NOT NULL;
ALTER TABLE `events_category` ADD COLUMN `project_id` BIGINT NOT NULL;
ALTER TABLE `campaign_stats` ADD COLUMN `project_id` BIGINT NOT NULL;
```

### 3-2. FK 제약 보강

현재 ERD 200~219줄에 FK 3개만 정의됨. 최소 추가:

```sql
-- campaigns
ALTER TABLE campaigns ADD FK (project_id) REFERENCES projects(project_id);
ALTER TABLE campaigns ADD FK (segment_id) REFERENCES segments(segment_id);
ALTER TABLE campaigns ADD FK (member_id)  REFERENCES members(member_id);

-- events / devices / users
ALTER TABLE events   ADD FK (project_id) REFERENCES projects(project_id);
ALTER TABLE devices  ADD FK (project_id) REFERENCES projects(project_id);
ALTER TABLE users    ADD FK (project_id) REFERENCES projects(project_id);

-- segments
ALTER TABLE segments      ADD FK (project_id) REFERENCES projects(project_id);
ALTER TABLE segment_rules ADD FK (segment_id) REFERENCES segments(segment_id);

-- notification_logs
ALTER TABLE notification_logs ADD FK (project_id)  REFERENCES projects(project_id);
ALTER TABLE notification_logs ADD FK (campaign_id) REFERENCES campaigns(campaign_id);
ALTER TABLE notification_logs ADD FK (device_id)   REFERENCES devices(device_id);

-- 집계 테이블
ALTER TABLE campaign_metric_daily ADD FK (campaign_id) REFERENCES campaigns(campaign_id);
ALTER TABLE segment_metric_daily  ADD FK (segment_id)  REFERENCES segments(segment_id);
```

### 3-3. 세그먼트 AND/OR 중첩 스키마 (✅ 2026-04-21 합의 완료: JSON 채택)

#### 문제

- requirements 3-3 필수: "AND/OR 중첩 조건 지원"
- 현재 `segment_rules`는 평탄한 리스트 → AND 평탄 조합만 가능

#### 결정

조건 트리를 JSON 컬럼으로 저장하는 방식으로 확정 (역할 3 주도).

```sql
ALTER TABLE `segments` ADD COLUMN `condition_tree` JSON NOT NULL COMMENT '조건 트리 (AND/OR 중첩)';
-- segment_rules는 유지하되 조회 최적화용 평탄 인덱스로 격하
```

트리 예:
```json
{
  "op": "AND",
  "children": [
    { "op": "LEAF", "ruleType": "TAG", "field": "region", "operator": "EQ", "value": "서울" },
    {
      "op": "OR",
      "children": [
        { "op": "LEAF", "ruleType": "TAG", "field": "grade", "operator": "EQ", "value": "VIP" },
        { "op": "LEAF", "ruleType": "EVENT", "field": "purchase_complete", "operator": "GTE", "value": "3" }
      ]
    }
  ]
}
```

역할 3 주도. 분석 API 61(세그먼트별 성과)과 직결되므로 합의 필요.

### 3-4. 주석/문서 정리

- `users.project_id` 주석 `'프로젝트 고유 ID (tenant_id)'` → **오류**. "소속 프로젝트 ID"로 수정.
- `campaigns.channel` 주석 `'발송 채널 -> 테이블 분리 필요'` → MVP 이후 계획은 별도 문서로, enum 주석에서 제거.
- `events.event_data VARCHAR(255)` → 속성 수집 기능 생각하면 좁음. `TEXT` 또는 `JSON` 권장.

---

## 4. 분석 API 수정 제안

### 4-1. API 59 (캠페인 성과 비교) 필터 확장

```
# 기존
?from=2026-04-01&to=2026-04-20&page=0&size=10

# 추가
?from=...&to=...&channel=PUSH&segmentId=1&status=COMPLETED&page=...&size=...
```

### 4-2. 응답 일관성

모든 분석 API 응답에 **집계 기준 시각**을 포함:

```json
{
  ...
  "metricsComputedAt": "2026-04-21T00:10:00"
}
```

"지금 보는 숫자가 얼마나 최신인지" 대시보드에서 표기.

### 4-3. `totalCampaigns` 의미 명시

대시보드 응답의 `totalCampaigns` → `campaignsSentInPeriod`로 이름 변경. "해당 기간에 한 번이라도 발송된 캠페인 수"로 정의.

### 4-4. 채널별/세그먼트별 응답에 빈 배열 처리

MVP는 PUSH 단일 채널이라 API 60 응답이 채널 1개만 오는 것이 정상. 스펙에 "MVP 기준 PUSH 단일 결과" 주석 추가.

### 4-5. 분석 API 추가 제안 (선택)

| API | 설명 | 최소 권한 |
|---|---|---|
| `GET /api/projects/{projectId}/analytics/campaigns/{id}/timeseries?granularity=day` | 캠페인 단위 시계열 | VIEWER |
| `GET /api/projects/{projectId}/analytics/export?type=campaigns&from=..&to=..` | CSV 내보내기 | VIEWER |

Export는 운영 현장에서 거의 항상 요구됨. 집계 테이블 기반이라 구현 비용 낮음. MVP 포함 권장.

---

## 5. API 전반 일관성 이슈

### 5-1. 응답 포맷 일관성

00-common.md는 `{ success, data, error }` 래핑을 규정했으나, 각 도메인 API 예시들은 **원시 응답 객체**(래핑 안 됨)로 쓰여 있다. 문서 예시를 모두 래핑된 형태로 통일하거나, "예시는 `data` 내부만 표시"를 common에 명시해야 한다. 이 채로 두면 프런트 통합 시 혼선 확실.

### 5-2. SDK API vs Management API 베이스 경로

- SDK: `/sdk/v1/...`
- Management: `/api/projects/{projectId}/...`

역할 2의 `/sdk/v1/users/identify`에서 `project_id`를 어떻게 전달할지 명시 없음. API Key로 프로젝트를 유추하는 설계라면 common에 한 줄 추가 필요.

### 5-3. 페이지네이션 `totalPages` 누락

`/campaigns` 등 일부 페이지 응답에 `totalPages` 필드가 빠져 있다. 00-common.md 규정 위반 (또는 스펙 예시 오류).

---

## 6. 합의 필요 의사결정 체크리스트

| # | 항목 | 결정권자 | Blocker? |
|---|------|---------|---------|
| 1 | 집계 테이블 3종 ERD 채택 | 전체 | Y |
| 2 | `notification_logs.project_id` 추가 + 인덱스 | 역할 5 + 6 | Y |
| 3 | 발송 카운트 규칙 (타임스탬프 기준 권장) | 역할 5 + 6 | Y |
| 4 | SDK 콜백 서명 토큰 도입 여부 | 역할 5 | Y |
| 5 | 캠페인 도메인 재설계 (정의/회차 분리, templates/channel_credentials 신설) | 역할 4 | ✅ 2026-04-21 확정 |
| 6 | `audit_logs` 테이블 확정 | 역할 1 | ✅ 2026-04-21 확정 (ADR-0004) |
| 7 | 전환 이벤트 MVP 포함/제외 | 전체 | N |
| 8 | 프로젝트 타임존 정책 | 전체 | ✅ 2026-04-21 확정 (MVP 한국 한정, Asia/Seoul 서버 고정) |
| 9 | 세그먼트 `condition_tree` JSON 도입 | 역할 3 | ✅ 2026-04-21 확정 (ADR-0001) |
| 10 | `clickRate` 분모 정의 (delivered vs sent) | 역할 6 | N |
| 11 | API 응답 포맷 예시 통일 | 전체 | N |
| 12 | Export API 포함 여부 | 역할 6 | N |
| 13 | 공통 인증/인가 필터 배치 및 `@PreAuthorize` 통일 | 역할 1 | ✅ 2026-04-21 확정 (ADR-0005) |

---

## 7. 제안 처리 순서

1. **금주**: 1~4번 Blocker 4건 팀 합의 → ERD/API 문서 업데이트
2. **다음 주 전반**: 5~10번 High/Medium 처리
3. **다음 주 후반**: 11~12번 정리 후 각 도메인 구현 착수
4. 구현 중반에 **중간 통합 체크포인트** 2회 — 역할 6 주도 (requirements 10-3)

---

## 8. 참고

- 원본 ERD: [backend/docs/erd.md](../erd.md)
- 공통 API 규칙: [backend/docs/api/00-common.md](../api/00-common.md)
- 분석 API: [backend/docs/api/06-analytics.md](../api/06-analytics.md)
- 요구사항: [backend/docs/requirements.md](../requirements.md)
