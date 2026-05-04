# Flyway 베이스라인 V1/V2/V3 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 첨부 ERD를 베이스라인으로 통합해 V1/V2/V3 마이그레이션을 `butterfly-api`에 작성하고, 기존 reporting 마이그레이션을 흡수·삭제한다.

**Architecture:** V1(core, B그룹 22개) + V2(reporting, A그룹 10개) + V3(audit_logs 트리거)으로 3분할. FK는 ADR-0013 §5 정책에 따라 후속 V로 분리. worker는 Flyway 비활성화하고 api가 마이그레이션 주체.

**Tech Stack:** Flyway (flyway-core, flyway-mysql), Spring Boot 3.5, MySQL 8, JPA, Gradle 멀티모듈.

**Spec:** [docs/superpowers/specs/2026-05-04-flyway-baseline-v1-design.md](../specs/2026-05-04-flyway-baseline-v1-design.md)

---

## File Structure

**Created:**
- `backend/butterfly-api/src/main/resources/db/migration/V1__core_baseline.sql` — 22개 테이블 (B그룹), 첨부 ERD 정제
- `backend/butterfly-api/src/main/resources/db/migration/V2__reporting_baseline.sql` — 10개 테이블 (A그룹), reporting V1+V2+V3+V5+V6+V7+V8+V9+V10+V11 + V13 message_key 통합
- `backend/butterfly-api/src/main/resources/db/migration/V3__audit_triggers.sql` — V4의 트리거 그대로

**Modified:**
- `backend/butterfly-api/src/main/resources/application.properties` — Flyway 주석 갱신, ddl-auto 일몰 조건 명시
- `backend/butterfly-worker/src/main/resources/application.properties` — Flyway 비활성화
- `backend/butterfly-api/build.gradle` — Flyway 의존성 주석 갱신

**Deleted:**
- `backend/butterfly-reporting/src/main/resources/db/migration/V1__create_notification_logs.sql` ~ `V13__add_notification_logs_message_key.sql` (총 13개 파일)
- `backend/butterfly-reporting/db/schema_analytics_audit.sql`

---

## Task 1: 사전 작업 — 도메인 엔티티의 PK·UK 검증

엔티티에 이미 정의된 복합 PK가 spec의 §4.5 추론과 일치하는지 확인하고, 어긋나면 V1 작성 시 엔티티 정의를 우선한다.

**Files:**
- Read: `backend/butterfly-event/src/main/java/com/b101/butterfly/event/entity/EventDailyAggId.java`
- Read: `backend/butterfly-event/src/main/java/com/b101/butterfly/event/entity/EventDailyAgg.java`
- Read: `backend/butterfly-segment/src/main/java/com/b101/butterfly/segment/entity/SegmentMemberId.java`
- Read: `backend/butterfly-segment/src/main/java/com/b101/butterfly/segment/entity/SegmentFavoriteId.java`
- Read: `backend/butterfly-event/src/main/java/com/b101/butterfly/event/entity/EventCategory.java`
- Read: `backend/butterfly-tenant/src/main/java/com/b101/butterfly/tenant/entity/Project.java`
- Read: `backend/butterfly-auth/src/main/java/com/b101/butterfly/auth/entity/Member.java`

- [ ] **Step 1: 복합 PK 엔티티 3종 PK 컬럼 명시 확인**

이미 확인된 사실 (spec과 일치):
- `EventDailyAggId(logDate, projectId, categoryId, userId)` ✓
- `SegmentMemberId(segmentId, userId)` ✓
- `SegmentFavoriteId(segmentId, memberId)` ✓

다음 컬럼 매핑은 V1 작성 시 그대로 적용:
- `event_daily_agg`: PK = (`log_date`, `project_id`, `category_id`, `user_id`)
- `segment_members`: PK = (`segment_id`, `user_id`)
- `segment_favorites`: PK = (`segment_id`, `member_id`)

- [ ] **Step 2: 단일 PK 엔티티의 컬럼명 확인**

각 엔티티 파일을 열어 `@Id` 컬럼명이 ERD와 일치하는지 확인. 어긋나면 V1 작성 시 엔티티 컬럼명 우선.

확인 대상:
- `Project.project_id`
- `Member.member_id`
- `Application.application_id`
- `User.user_id`, `Device.device_id`, `Event.event_id`, `EventCategory.category_id`
- `Segment.segment_id`
- `RefreshToken.token_id`, `TokenBlacklist.blacklist_id`

- [ ] **Step 3: 누락된 도메인 확인 — 추론으로 진행**

다음 도메인은 엔티티가 아직 없으므로 spec §4.5의 PK 추론을 그대로 사용:
- `campaigns`, `campaign_executions`, `message_templates` (campaign 모듈)
- `event_schemas`, `event_schema_fields` (event 모듈)
- `user_tags`, `tag_definitions` (확인 필요 — 검색)
- `segment_snapshots`, `segment_snapshot_users` (segment 모듈, 엔티티 없음)

검색 명령:
```bash
find backend -name "Campaign.java" -o -name "MessageTemplate.java" -o -name "EventSchema.java" -o -name "UserTag.java" -o -name "TagDefinition.java" -o -name "SegmentSnapshot*.java" 2>/dev/null
```
Expected: 대부분 빈 결과 (엔티티 미생성). 빈 결과면 spec 추론대로 진행.

- [ ] **Step 4: 결과 정리 (커밋 없음, Task 2로 메모만 전달)**

PK·UK 결정 표를 머릿속/스크래치에 정리:
- 복합 PK 3종 (위 Step 1 결과)
- 단일 PK 17종 (Step 2/3 확인 결과)
- 명백한 복합 UK 5종 (spec §4.5-8):
  - `user_tags(project_id, user_id, tag_key)`
  - `event_categories(project_id, name)`
  - `tag_definitions(project_id, tag_key)`
  - `event_schemas(project_id, category_id, version)`
  - `event_schema_fields(schema_id, field_name)`

---

## Task 2: V1 — core_baseline.sql 작성 (B그룹 22개 테이블)

**Files:**
- Create: `backend/butterfly-api/src/main/resources/db/migration/V1__core_baseline.sql`

- [ ] **Step 1: 마이그레이션 디렉토리 생성**

```bash
mkdir -p backend/butterfly-api/src/main/resources/db/migration
```

- [ ] **Step 2: V1 파일 작성 (헤더 + tenant 도메인 4개 테이블)**

`backend/butterfly-api/src/main/resources/db/migration/V1__core_baseline.sql` 신규 작성:

```sql
-- =============================================================================
-- V1: core_baseline (B그룹 22개 테이블)
-- 출처: 첨부 ERD (butterfly-erd.sql) 정제
-- 정제 규칙: PRIMARY KEY 명시, FK 컬럼 AUTO_INCREMENT 제거, 오타 수정,
--           mojibake COMMENT 제거, ENGINE=InnoDB CHARSET=utf8mb4
-- FK는 V1에 없음 (ADR-0013 §5 — 후속 V로 분리)
-- 도메인 엔티티 ↔ 스키마 정합성은 도메인별 후속 티켓에서 검증
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Tenant 도메인 (projects, members, applications)
-- -----------------------------------------------------------------------------

CREATE TABLE `projects` (
    `project_id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `name`               VARCHAR(255) NOT NULL,
    `business_no`        VARCHAR(50)  NOT NULL,
    `company_auth_no`    VARCHAR(100) NOT NULL,
    `owner_id`           BIGINT       NOT NULL,
    `public_api_key`     VARCHAR(100) NULL,
    `fcm_credentials`    TEXT         NULL,
    `daily_limit`        INT          NULL,
    `plan`               ENUM('FREE','BASIC','PRO','ENTERPRISE') NOT NULL DEFAULT 'FREE',
    `is_deleted`         BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted_at`         DATETIME     NULL,
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`project_id`),
    KEY `idx_projects_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `members` (
    `member_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`     BIGINT       NULL,
    `email`          VARCHAR(255) NOT NULL,
    `password_hash`  VARCHAR(255) NULL,
    `name`           VARCHAR(20)  NOT NULL,
    `phone`          VARCHAR(11)  NOT NULL,
    `birth_date`     DATE         NOT NULL,
    `role_type`      ENUM('ADMIN','OWNER','EDITOR','VIEWER') NOT NULL DEFAULT 'VIEWER',
    `is_deleted`     BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted_at`     DATETIME     NULL,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`member_id`),
    UNIQUE KEY `uk_members_email` (`email`),
    KEY `idx_members_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `applications` (
    `application_id`  BIGINT       NOT NULL AUTO_INCREMENT,
    `applicant_name`  VARCHAR(100) NOT NULL,
    `email`           VARCHAR(255) NOT NULL,
    `phone`           VARCHAR(11)  NOT NULL,
    `birth_date`      DATE         NOT NULL,
    `business_name`   VARCHAR(255) NOT NULL,
    `business_no`     VARCHAR(50)  NOT NULL,
    `status`          ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    `reject_reason`   TEXT         NULL,
    `reviewed_by`     BIGINT       NULL,
    `reviewed_at`     DATETIME     NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`application_id`),
    KEY `idx_applications_status_time` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 3: V1 — Auth 도메인 테이블 추가 (refresh_tokens, token_blacklist)**

같은 파일에 이어서:

```sql
-- -----------------------------------------------------------------------------
-- Auth 도메인 (refresh_tokens, token_blacklist)
-- -----------------------------------------------------------------------------

CREATE TABLE `refresh_tokens` (
    `token_id`    BIGINT       NOT NULL AUTO_INCREMENT,
    `member_id`   BIGINT       NOT NULL,
    `token`       VARCHAR(500) NOT NULL,
    `expires_at`  DATETIME     NOT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`token_id`),
    UNIQUE KEY `uk_refresh_tokens_token` (`token`),
    KEY `idx_refresh_tokens_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `token_blacklist` (
    `blacklist_id`  BIGINT       NOT NULL AUTO_INCREMENT,
    `token`         VARCHAR(500) NOT NULL,
    `expires_at`    DATETIME     NOT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`blacklist_id`),
    KEY `idx_token_blacklist_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 4: V1 — Event 도메인 7개 테이블 추가 (users, devices, events, event_categories, event_schemas, event_schema_fields, event_daily_agg, user_tags, tag_definitions)**

같은 파일에 이어서:

```sql
-- -----------------------------------------------------------------------------
-- Event 도메인 (users, devices, events, event_categories, event_schemas,
--             event_schema_fields, event_daily_agg, user_tags, tag_definitions)
-- -----------------------------------------------------------------------------

CREATE TABLE `users` (
    `user_id`       BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`    BIGINT       NOT NULL,
    `external_id`   VARCHAR(255) NOT NULL,
    `email`         VARCHAR(255) NULL,
    `phone`         VARCHAR(20)  NULL,
    `birth_date`    DATE         NULL,
    `push_opt_in`   BOOLEAN      NOT NULL DEFAULT TRUE,
    `signed_up_at`  DATETIME     NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_users_project_external` (`project_id`, `external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `devices` (
    `device_id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`              BIGINT       NOT NULL,
    `user_id`                 BIGINT       NOT NULL,
    `platform`                ENUM('ANDROID','IOS','WEB') NOT NULL,
    `push_token`              VARCHAR(500) NOT NULL,
    `push_token_updated_at`   DATETIME     NOT NULL,
    `token_valid`             BOOLEAN      NOT NULL DEFAULT TRUE,
    `app_version`             VARCHAR(50)  NOT NULL,
    `os_version`              VARCHAR(50)  NOT NULL,
    `last_active_at`          DATETIME     NULL,
    `created_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`device_id`),
    KEY `idx_devices_user` (`user_id`),
    KEY `idx_devices_project_active` (`project_id`, `last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `event_categories` (
    `category_id`  BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`   BIGINT       NOT NULL,
    `name`         VARCHAR(100) NOT NULL,
    `type`         ENUM('DEFAULT','CUSTOM') NOT NULL DEFAULT 'CUSTOM',
    `description`  VARCHAR(500) NULL,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`category_id`),
    UNIQUE KEY `uk_event_categories_project_name` (`project_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `events` (
    `event_id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`      BIGINT       NOT NULL,
    `user_id`         BIGINT       NOT NULL,
    `category_id`     BIGINT       NOT NULL,
    `device_id`       BIGINT       NOT NULL,
    `event_uuid`      VARCHAR(36)  NOT NULL,
    `occurred_at`     DATETIME(3)  NOT NULL,
    `schema_version`  INT          NOT NULL DEFAULT 1,
    `event_data`      JSON         NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`event_id`),
    UNIQUE KEY `uk_events_uuid` (`event_uuid`),
    KEY `idx_events_project_time` (`project_id`, `occurred_at`),
    KEY `idx_events_user_time` (`user_id`, `occurred_at`),
    KEY `idx_events_category_time` (`category_id`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `event_schemas` (
    `schema_id`        BIGINT    NOT NULL AUTO_INCREMENT,
    `project_id`       BIGINT    NOT NULL,
    `category_id`      BIGINT    NOT NULL,
    `version`          INT       NOT NULL,
    `schema_def`       JSON      NOT NULL,
    `effective_from`   DATETIME  NOT NULL,
    `is_current`       BOOLEAN   NOT NULL DEFAULT FALSE,
    `created_by`       BIGINT    NOT NULL,
    `created_at`       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`schema_id`),
    UNIQUE KEY `uk_event_schemas_project_cat_version` (`project_id`, `category_id`, `version`),
    KEY `idx_event_schemas_current` (`category_id`, `is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `event_schema_fields` (
    `field_id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `schema_id`       BIGINT       NOT NULL,
    `field_name`      VARCHAR(100) NOT NULL,
    `field_type`      ENUM('STRING','NUMBER','BOOLEAN','DATE') NOT NULL DEFAULT 'STRING',
    `is_required`     BOOLEAN      NOT NULL DEFAULT FALSE,
    `is_promoted`     BOOLEAN      NOT NULL DEFAULT FALSE,
    `previous_names`  JSON         NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`field_id`),
    UNIQUE KEY `uk_event_schema_fields_schema_name` (`schema_id`, `field_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `event_daily_agg` (
    `log_date`     DATE         NOT NULL,
    `project_id`   BIGINT       NOT NULL,
    `category_id`  BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL,
    `event_count`  INT          NOT NULL DEFAULT 0,
    `first_at`     DATETIME(3)  NOT NULL,
    `last_at`      DATETIME(3)  NOT NULL,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`log_date`, `project_id`, `category_id`, `user_id`),
    KEY `idx_event_daily_agg_project_date` (`project_id`, `log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_tags` (
    `user_tag_id`  BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`   BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL,
    `tag_key`      VARCHAR(100) NOT NULL,
    `tag_value`    VARCHAR(500) NOT NULL,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`user_tag_id`),
    UNIQUE KEY `uk_user_tags_project_user_key` (`project_id`, `user_id`, `tag_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `tag_definitions` (
    `tag_def_id`    BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`    BIGINT       NOT NULL,
    `tag_key`       VARCHAR(100) NOT NULL,
    `display_name`  VARCHAR(100) NOT NULL,
    `tag_type`      ENUM('STRING','NUMBER','BOOLEAN') NOT NULL DEFAULT 'STRING',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`tag_def_id`),
    UNIQUE KEY `uk_tag_definitions_project_key` (`project_id`, `tag_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 5: V1 — Campaign 도메인 3개 테이블 추가 (message_templates, campaigns, campaign_executions)**

같은 파일에 이어서:

```sql
-- -----------------------------------------------------------------------------
-- Campaign 도메인 (message_templates, campaigns, campaign_executions)
-- -----------------------------------------------------------------------------

CREATE TABLE `message_templates` (
    `message_template_id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `member_id`                        BIGINT       NOT NULL,
    `project_id`                       BIGINT       NOT NULL,
    `message_templates_name`           VARCHAR(255) NOT NULL,
    `message_templates_description`    VARCHAR(500) NOT NULL,
    `channel`                          ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL DEFAULT 'PUSH',
    `message_templates_title`          VARCHAR(255) NOT NULL,
    `message_templates_body`           TEXT         NOT NULL,
    `message_templates_image_url`      VARCHAR(500) NULL,
    `required_variables`               JSON         NOT NULL,
    `is_deleted`                       BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted_at`                       DATETIME     NULL,
    `created_at`                       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`message_template_id`),
    KEY `idx_message_templates_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `campaigns` (
    `campaign_id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`                 BIGINT       NOT NULL,
    `message_template_id`        BIGINT       NOT NULL,
    `segment_id`                 BIGINT       NULL,
    `member_id`                  BIGINT       NOT NULL,
    `campaign_name`              VARCHAR(255) NOT NULL,
    `campaign_description`       VARCHAR(500) NOT NULL,
    `campaign_type`              ENUM('ONE_TIME','AUTOMATION') NOT NULL DEFAULT 'ONE_TIME',
    `channel`                    ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL DEFAULT 'PUSH',
    `campaign_status`            ENUM('DRAFT','ACTIVE','PAUSED','ENDED') NOT NULL DEFAULT 'DRAFT',
    `send_mode`                  ENUM('IMMEDIATE','SCHEDULED','RECURRING') NOT NULL,
    `recurring_cron`             VARCHAR(50)  NULL,
    `repeat_type`                ENUM('DAILY','WEEKLY','MONTHLY') NULL,
    `repeat_day_of_week`         VARCHAR(50)  NULL,
    `repeat_day_of_month`        INT          NULL,
    `repeat_time`                TIME         NULL,
    `recurring_count`            INT          NULL,
    `recurring_execution_count`  INT          NOT NULL DEFAULT 0,
    `next_run_at`                DATETIME     NULL,
    `version`                    INT          NOT NULL DEFAULT 0,
    `is_deleted`                 BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted_at`                 DATETIME     NULL,
    `created_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`campaign_id`),
    KEY `idx_campaigns_project_status` (`project_id`, `campaign_status`),
    KEY `idx_campaigns_next_run` (`next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `campaign_executions` (
    `campaign_execution_id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `campaign_id`                   BIGINT       NOT NULL,
    `run_round`                     INT          NOT NULL,
    `campaign_execution_status`     ENUM('READY','SEND','FAIL','SUCCESS') NOT NULL,
    `send_started_at`               DATETIME     NOT NULL,
    `send_completed_at`             DATETIME     NOT NULL,
    `target_count`                  INT          NOT NULL DEFAULT 0,
    `sent_count`                    INT          NOT NULL DEFAULT 0,
    `failed_count`                  INT          NOT NULL DEFAULT 0,
    `fail_reason`                   VARCHAR(500) NULL,
    `is_deleted`                    BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted_at`                    DATETIME     NULL,
    `created_at`                    DATETIME     NOT NULL,
    `updated_at`                    DATETIME     NOT NULL,

    PRIMARY KEY (`campaign_execution_id`),
    UNIQUE KEY `uk_campaign_executions_campaign_round` (`campaign_id`, `run_round`),
    KEY `idx_campaign_executions_status` (`campaign_execution_status`, `send_started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 6: V1 — Segment 도메인 5개 테이블 추가 (segments, segment_members, segment_favorites, segment_snapshots, segment_snapshot_users)**

같은 파일에 이어서:

```sql
-- -----------------------------------------------------------------------------
-- Segment 도메인 (segments, segment_members, segment_favorites,
--                 segment_snapshots, segment_snapshot_users)
-- -----------------------------------------------------------------------------

CREATE TABLE `segments` (
    `segment_id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`        BIGINT       NOT NULL,
    `name`              VARCHAR(30)  NOT NULL,
    `description`       VARCHAR(100) NULL,
    `condition_tree`    JSON         NOT NULL,
    `schema_version`    INT          NOT NULL DEFAULT 1,
    `estimated_count`   INT          NULL DEFAULT 0,
    `estimated_at`      DATETIME     NULL,
    `last_built_at`     DATETIME     NULL,
    `build_status`      ENUM('PENDING','BUILDING','READY','FAILED') NOT NULL DEFAULT 'PENDING',
    `created_by`        BIGINT       NOT NULL,
    `is_deleted`        BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted_at`        DATETIME     NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`segment_id`),
    UNIQUE KEY `uk_segments_project_name` (`project_id`, `name`),
    KEY `idx_segments_project_status` (`project_id`, `build_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `segment_members` (
    `segment_id`   BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL,
    `matched_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `expires_at`   DATETIME     NULL,

    PRIMARY KEY (`segment_id`, `user_id`),
    KEY `idx_segment_members_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `segment_favorites` (
    `segment_id`  BIGINT    NOT NULL,
    `member_id`   BIGINT    NOT NULL,
    `created_at`  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`segment_id`, `member_id`),
    KEY `idx_segment_favorites_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `segment_snapshots` (
    `snapshot_id`     BIGINT    NOT NULL AUTO_INCREMENT,
    `segment_id`      BIGINT    NOT NULL,
    `campaign_id`     BIGINT    NOT NULL,
    `target_count`    INT       NOT NULL DEFAULT 0,
    `created_at`      DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`snapshot_id`),
    KEY `idx_segment_snapshots_campaign` (`campaign_id`),
    KEY `idx_segment_snapshots_segment_time` (`segment_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `segment_snapshot_users` (
    `snapshot_id`  BIGINT  NOT NULL,
    `user_id`      BIGINT  NOT NULL,

    PRIMARY KEY (`snapshot_id`, `user_id`),
    KEY `idx_segment_snapshot_users_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 7: V1 작성 검증 — 파일 라인 카운트와 테이블 수 확인**

```bash
grep -c "^CREATE TABLE" backend/butterfly-api/src/main/resources/db/migration/V1__core_baseline.sql
```
Expected: `22`

```bash
wc -l backend/butterfly-api/src/main/resources/db/migration/V1__core_baseline.sql
```
Expected: 250 라인 이상

- [ ] **Step 8: 커밋**

```bash
git add backend/butterfly-api/src/main/resources/db/migration/V1__core_baseline.sql
git commit -m "feat(api): V1 core_baseline 마이그레이션 추가 (B그룹 22개 테이블)"
```

---

## Task 3: V2 — reporting_baseline.sql 작성 (A그룹 10개 테이블)

기존 reporting V1+V2+V3+V5+V6+V7+V8+V9+V10+V11을 통합하고, V13의 `notification_logs.message_key` 컬럼·UK를 V1의 `notification_logs` 정의에 합쳐서 한 번에 만든다.

**Files:**
- Create: `backend/butterfly-api/src/main/resources/db/migration/V2__reporting_baseline.sql`
- Read (참조용): `backend/butterfly-reporting/src/main/resources/db/migration/V1__create_notification_logs.sql` ~ `V13`

- [ ] **Step 1: V2 헤더 + notification_logs (V1 + V13 통합)**

`backend/butterfly-api/src/main/resources/db/migration/V2__reporting_baseline.sql` 신규 작성:

```sql
-- =============================================================================
-- V2: reporting_baseline (A그룹 10개 테이블)
-- 출처: butterfly-reporting/src/main/resources/db/migration/V1~V11 통합 + V13 컬럼 흡수
-- 정의는 reporting 모듈의 정제된 정의를 그대로 사용 (충돌 시 reporting 우선 — spec §4.3)
-- FK는 V2에 없음 (ADR-0013 §5 — 후속 V로 분리)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- notification_logs (출처: reporting V1 + V13 message_key 통합)
-- 발송 1건 = 1 row, 상태 전이는 UPDATE
-- =============================================================================

CREATE TABLE `notification_logs` (
    `log_id`                 BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '로그 고유 ID',
    `project_id`             BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`            BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `campaign_execution_id`  BIGINT       NULL                     COMMENT '캠페인 실행 회차 ID (반복 캠페인 회차 추적)',
    `message_template_id`    BIGINT       NULL                     COMMENT '사용된 템플릿 ID',
    `user_id`                BIGINT       NULL                     COMMENT '유저 ID',
    `device_id`              BIGINT       NULL                     COMMENT '기기 ID',
    `channel`                ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '발송 채널',
    `status`                 ENUM('PENDING','SENT','DELIVERED','FAILED') NOT NULL COMMENT '발송 상태 (CLICKED는 user_action_logs로 분리)',
    `error_code`             VARCHAR(50)  NULL                     COMMENT '에러 코드',
    `error_message`          TEXT         NULL                     COMMENT '에러 메시지',
    `sent_at`                DATETIME     NULL                     COMMENT '발송 시각',
    `delivered_at`           DATETIME     NULL                     COMMENT '도달 시각',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '로그 생성 시각',
    `message_key`            VARCHAR(128) NULL                     COMMENT 'Kafka 메시지 멱등성 키 (notification-send-result)',

    PRIMARY KEY (`log_id`),
    UNIQUE KEY `uk_notification_logs_message_key`           (`message_key`),
    KEY `idx_notification_logs_project_time`    (`project_id`, `created_at`),
    KEY `idx_notification_logs_campaign_status` (`campaign_id`, `status`),
    KEY `idx_notification_logs_execution`       (`campaign_execution_id`),
    KEY `idx_notification_logs_template`        (`message_template_id`),
    KEY `idx_notification_logs_user_time`       (`user_id`, `created_at`),
    KEY `idx_notification_logs_channel_status`  (`channel`, `status`),
    KEY `idx_notification_logs_error`           (`error_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='알림 발송 로그 (발송 1건 = 1 row, 상태 전이는 UPDATE)';
```

- [ ] **Step 2: V2 — user_action_logs (출처: reporting V2)**

같은 파일에 이어서, reporting V2 내용을 그대로 옮긴다 (변경 없음):

```sql
-- -----------------------------------------------------------------------------
-- user_action_logs (출처: reporting V2)
-- =============================================================================

CREATE TABLE `user_action_logs` (
    `action_id`      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '액션 고유 ID',
    `log_id`         BIGINT       NOT NULL                 COMMENT 'notification_logs.log_id',
    `project_id`     BIGINT       NOT NULL                 COMMENT '조회 성능용 비정규화',
    `campaign_id`    BIGINT       NOT NULL                 COMMENT '조회 성능용 비정규화',
    `user_id`        BIGINT       NULL                     COMMENT '유저 ID',
    `device_id`      BIGINT       NULL                     COMMENT '기기 ID (이메일/웹 클릭은 NULL)',
    `action_type`    ENUM('OPEN','CLICK','CONVERT','DISMISS') NOT NULL COMMENT '행동 종류',
    `action_target`  VARCHAR(500) NULL                     COMMENT '클릭한 링크 URL 등',
    `user_agent`     VARCHAR(500) NULL                     COMMENT 'User-Agent',
    `action_at`      DATETIME     NOT NULL                 COMMENT '행동 발생 시각',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '로그 생성 시각',

    PRIMARY KEY (`action_id`),
    KEY `idx_user_action_logs_log`             (`log_id`),
    KEY `idx_user_action_logs_campaign_action` (`campaign_id`, `action_type`, `action_at`),
    KEY `idx_user_action_logs_user_time`       (`user_id`, `action_at`),
    KEY `idx_user_action_logs_project_time`    (`project_id`, `action_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='사용자 반응 로그 (OPEN/CLICK/CONVERT/DISMISS)';
```

- [ ] **Step 3: V2 — audit_logs (출처: reporting V3)**

같은 파일에 이어서, reporting V3 내용을 그대로 옮긴다:

```sql
-- -----------------------------------------------------------------------------
-- audit_logs (출처: reporting V3) — append-only, 트리거는 V3 마이그레이션에서 생성
-- =============================================================================

CREATE TABLE `audit_logs` (
    `audit_id`     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '감사 로그 ID',
    `project_id`   BIGINT       NULL                     COMMENT '프로젝트 ID (시스템 레벨 시 NULL)',
    `member_id`    BIGINT       NOT NULL                 COMMENT '작업 수행 회원 ID',
    `action`       VARCHAR(100) NOT NULL                 COMMENT '작업 종류 (FCM_KEY_UPDATE, API_KEY_REISSUE 등)',
    `target_type`  VARCHAR(50)  NOT NULL                 COMMENT '대상 엔티티 타입',
    `target_id`    BIGINT       NULL                     COMMENT '대상 엔티티 ID',
    `detail`       JSON         NULL                     COMMENT '추가 상세 (변경 전/후 값 등)',
    `ip_address`   VARCHAR(45)  NULL                     COMMENT '요청 IP (IPv6 대응)',
    `user_agent`   VARCHAR(500) NULL                     COMMENT 'User-Agent',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작업 시각',

    PRIMARY KEY (`audit_id`),
    KEY `idx_audit_logs_project_action` (`project_id`, `action`, `created_at`),
    KEY `idx_audit_logs_actor`          (`member_id`, `created_at`),
    KEY `idx_audit_logs_target`         (`target_type`, `target_id`),
    KEY `idx_audit_logs_action_time`    (`action`, `created_at`),
    KEY `idx_audit_logs_created_at`     (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='감사 로그 (append-only: INSERT/SELECT만 허용)';
```

- [ ] **Step 4: V2 — send_reconciliations + system_health_snapshots (출처: reporting V5, V6)**

같은 파일에 이어서, reporting V5 + V6 내용을 그대로 옮긴다:

```sql
-- -----------------------------------------------------------------------------
-- send_reconciliations (출처: reporting V5)
-- =============================================================================

CREATE TABLE `send_reconciliations` (
    `recon_id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '검증 ID',
    `project_id`             BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`            BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `campaign_execution_id`  BIGINT       NULL                     COMMENT '캠페인 실행 회차 ID',
    `channel`                ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `expected_count`         INT          NOT NULL                 COMMENT '세그먼트 스냅샷 대상자 수',
    `actual_count`           INT          NOT NULL                 COMMENT '실제 notification_logs 기록 수',
    `sent_count`             INT          NOT NULL DEFAULT 0       COMMENT 'SENT 상태 수',
    `failed_count`           INT          NOT NULL DEFAULT 0       COMMENT 'FAILED 상태 수',
    `diff_count`             INT          NOT NULL                 COMMENT 'expected - actual',
    `is_consistent`          BOOLEAN      NOT NULL                 COMMENT 'diff == 0',
    `note`                   VARCHAR(500) NULL                     COMMENT '비고',
    `checked_at`             DATETIME     NOT NULL                 COMMENT '검증 수행 시각',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',

    PRIMARY KEY (`recon_id`),
    KEY `idx_send_reconciliations_campaign`     (`campaign_id`, `checked_at`),
    KEY `idx_send_reconciliations_execution`    (`campaign_execution_id`),
    KEY `idx_send_reconciliations_inconsistent` (`is_consistent`, `checked_at`),
    KEY `idx_send_reconciliations_project_time` (`project_id`, `checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='발송 정합성 검증 결과 (캠페인 회차 × 채널 단위)';

-- -----------------------------------------------------------------------------
-- system_health_snapshots (출처: reporting V6)
-- =============================================================================

CREATE TABLE `system_health_snapshots` (
    `snapshot_id`     BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '스냅샷 ID',
    `metric_name`     VARCHAR(50)   NOT NULL                 COMMENT 'kafka_lag, worker_tps, api_error_rate 등',
    `metric_target`   VARCHAR(100)  NULL                     COMMENT 'topic명, worker명, API path',
    `metric_value`    DECIMAL(18,4) NOT NULL                 COMMENT '측정값',
    `threshold_warn`  DECIMAL(18,4) NULL                     COMMENT '경고 임계치',
    `threshold_crit`  DECIMAL(18,4) NULL                     COMMENT '위험 임계치',
    `status`          ENUM('OK','WARN','CRITICAL') NOT NULL  COMMENT '상태',
    `collected_at`    DATETIME      NOT NULL                 COMMENT '수집 시각',

    PRIMARY KEY (`snapshot_id`),
    KEY `idx_system_health_snapshots_metric_time` (`metric_name`, `collected_at`),
    KEY `idx_system_health_snapshots_status_time` (`status`, `collected_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='시스템 건강 상태 스냅샷';
```

- [ ] **Step 5: V2 — campaign_stats_hourly + campaign_stats_daily (출처: reporting V7, V8)**

같은 파일에 이어서:

```sql
-- -----------------------------------------------------------------------------
-- campaign_stats_hourly (출처: reporting V7)
-- =============================================================================

CREATE TABLE `campaign_stats_hourly` (
    `stat_id`          BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`       BIGINT      NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`      BIGINT      NOT NULL                 COMMENT '캠페인 ID',
    `channel`          ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `bucket_hour`      DATETIME    NOT NULL                 COMMENT '시간 버킷 (예: 2026-04-28 15:00:00)',
    `pending_count`    INT         NOT NULL DEFAULT 0       COMMENT '대기 수',
    `sent_count`       INT         NOT NULL DEFAULT 0       COMMENT '발송 수',
    `delivered_count`  INT         NOT NULL DEFAULT 0       COMMENT '도달 수',
    `failed_count`     INT         NOT NULL DEFAULT 0       COMMENT '실패 수',
    `open_count`       INT         NOT NULL DEFAULT 0       COMMENT 'user_action_logs OPEN',
    `click_count`      INT         NOT NULL DEFAULT 0       COMMENT 'user_action_logs CLICK',
    `convert_count`    INT         NOT NULL DEFAULT 0       COMMENT 'user_action_logs CONVERT',
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_campaign_stats_hourly_nat`        (`campaign_id`, `channel`, `bucket_hour`),
    KEY `idx_campaign_stats_hourly_project_hour`     (`project_id`, `bucket_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='캠페인 시간 단위 집계 (실시간 대시보드용)';

-- -----------------------------------------------------------------------------
-- campaign_stats_daily (출처: reporting V8)
-- =============================================================================

CREATE TABLE `campaign_stats_daily` (
    `stat_id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`       BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`      BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `channel`          ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `bucket_date`      DATE         NOT NULL                 COMMENT '일 버킷',
    `sent_count`       INT          NOT NULL DEFAULT 0       COMMENT '발송 수',
    `delivered_count`  INT          NOT NULL DEFAULT 0       COMMENT '도달 수',
    `failed_count`     INT          NOT NULL DEFAULT 0       COMMENT '실패 수',
    `open_count`       INT          NOT NULL DEFAULT 0       COMMENT '오픈 수',
    `click_count`      INT          NOT NULL DEFAULT 0       COMMENT '클릭 수',
    `convert_count`    INT          NOT NULL DEFAULT 0       COMMENT '전환 수',
    `delivery_rate`    DECIMAL(5,2) NULL                     COMMENT 'delivered / sent * 100',
    `open_rate`        DECIMAL(5,2) NULL                     COMMENT 'open / delivered * 100',
    `click_rate`       DECIMAL(5,2) NULL                     COMMENT 'click / open * 100',
    `convert_rate`     DECIMAL(5,2) NULL                     COMMENT 'convert / sent * 100',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_campaign_stats_daily_nat`        (`campaign_id`, `channel`, `bucket_date`),
    KEY `idx_campaign_stats_daily_project_date`     (`project_id`, `bucket_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='캠페인 일 단위 집계 (성과 리포트 API용)';
```

- [ ] **Step 6: V2 — template_stats_daily + failure_stats_daily + project_stats_daily (출처: reporting V9, V10, V11)**

같은 파일에 이어서:

```sql
-- -----------------------------------------------------------------------------
-- template_stats_daily (출처: reporting V9)
-- =============================================================================

CREATE TABLE `template_stats_daily` (
    `stat_id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`           BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `message_template_id`  BIGINT       NOT NULL                 COMMENT '메시지 템플릿 ID',
    `channel`              ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `bucket_date`          DATE         NOT NULL                 COMMENT '일 버킷',
    `used_campaign_count`  INT          NOT NULL DEFAULT 0       COMMENT '이 템플릿을 쓴 캠페인 수',
    `sent_count`           INT          NOT NULL DEFAULT 0       COMMENT '발송 수',
    `delivered_count`      INT          NOT NULL DEFAULT 0       COMMENT '도달 수',
    `open_count`           INT          NOT NULL DEFAULT 0       COMMENT '오픈 수',
    `click_count`          INT          NOT NULL DEFAULT 0       COMMENT '클릭 수',
    `convert_count`        INT          NOT NULL DEFAULT 0       COMMENT '전환 수',
    `open_rate`            DECIMAL(5,2) NULL                     COMMENT '오픈률 (%)',
    `click_rate`           DECIMAL(5,2) NULL                     COMMENT '클릭률 (%)',
    `convert_rate`         DECIMAL(5,2) NULL                     COMMENT '전환률 (%)',
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_template_stats_daily_nat`        (`message_template_id`, `channel`, `bucket_date`),
    KEY `idx_template_stats_daily_project_date`     (`project_id`, `bucket_date`),
    KEY `idx_template_stats_daily_top`              (`project_id`, `bucket_date`, `open_rate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='템플릿 일 단위 효율 분석';

-- -----------------------------------------------------------------------------
-- failure_stats_daily (출처: reporting V10)
-- =============================================================================

CREATE TABLE `failure_stats_daily` (
    `stat_id`                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`             BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `channel`                ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `error_code`             VARCHAR(50)  NOT NULL                 COMMENT 'INVALID_TOKEN, AUTH_ERROR 등',
    `bucket_date`            DATE         NOT NULL                 COMMENT '일 버킷',
    `failed_count`           INT          NOT NULL DEFAULT 0       COMMENT '실패 건수',
    `affected_campaign_cnt`  INT          NOT NULL DEFAULT 0       COMMENT '영향 받은 캠페인 수',
    `affected_user_cnt`      INT          NOT NULL DEFAULT 0       COMMENT '영향 받은 유저 수',
    `first_occurred_at`      DATETIME     NULL                     COMMENT '해당일 최초 발생 시각',
    `last_occurred_at`       DATETIME     NULL                     COMMENT '해당일 마지막 발생 시각',
    `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_failure_stats_daily_nat`         (`project_id`, `channel`, `error_code`, `bucket_date`),
    KEY `idx_failure_stats_daily_project_date`      (`project_id`, `bucket_date`),
    KEY `idx_failure_stats_daily_top`               (`project_id`, `bucket_date`, `failed_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='실패 원인 일 단위 집계';

-- -----------------------------------------------------------------------------
-- project_stats_daily (출처: reporting V11)
-- =============================================================================

CREATE TABLE `project_stats_daily` (
    `stat_id`                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`             BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `bucket_date`            DATE         NOT NULL                 COMMENT '일 버킷',
    `active_user_count`      INT          NOT NULL DEFAULT 0       COMMENT '당일 이벤트 발생 유저 수',
    `new_user_count`         INT          NOT NULL DEFAULT 0       COMMENT '신규 가입 유저 수',
    `churn_user_count`       INT          NOT NULL DEFAULT 0       COMMENT '이탈 세그먼트 매칭 수',
    `total_sent`             INT          NOT NULL DEFAULT 0       COMMENT '총 발송 수',
    `total_delivered`        INT          NOT NULL DEFAULT 0       COMMENT '총 도달 수',
    `total_failed`           INT          NOT NULL DEFAULT 0       COMMENT '총 실패 수',
    `total_open`             INT          NOT NULL DEFAULT 0       COMMENT '총 오픈 수',
    `total_click`            INT          NOT NULL DEFAULT 0       COMMENT '총 클릭 수',
    `total_convert`          INT          NOT NULL DEFAULT 0       COMMENT '총 전환 수',
    `push_open_rate`         DECIMAL(5,2) NULL                     COMMENT '앱 푸시 오픈률 (%)',
    `overall_click_rate`     DECIMAL(5,2) NULL                     COMMENT '전체 클릭률 (%)',
    `overall_convert_rate`   DECIMAL(5,2) NULL                     COMMENT '전체 전환률 (%)',
    `active_campaign_count`  INT          NOT NULL DEFAULT 0       COMMENT '활성 캠페인 수',
    `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_project_stats_daily_nat` (`project_id`, `bucket_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='프로젝트 일 단위 집계 (대시보드용)';
```

- [ ] **Step 7: V2 검증**

```bash
grep -c "^CREATE TABLE" backend/butterfly-api/src/main/resources/db/migration/V2__reporting_baseline.sql
```
Expected: `10`

- [ ] **Step 8: 커밋**

```bash
git add backend/butterfly-api/src/main/resources/db/migration/V2__reporting_baseline.sql
git commit -m "feat(api): V2 reporting_baseline 마이그레이션 추가 (A그룹 10개 테이블)"
```

---

## Task 4: V3 — audit_triggers.sql 작성

reporting V4의 트리거를 그대로 옮긴다. `flyway-mysql` 모듈이 BEGIN...END 블록을 인식하므로 DELIMITER 없이 작성.

**Files:**
- Create: `backend/butterfly-api/src/main/resources/db/migration/V3__audit_triggers.sql`

- [ ] **Step 1: V3 파일 작성**

```sql
-- =============================================================================
-- V3: audit_logs append-only 트리거 (출처: reporting V4)
-- ADR-0008 §append-only 3중 방어선 — 1차 방어 (실수 방지용)
-- 실질 방어는 후속 V의 DB 권한 분리 (S14P31B101-125)
--
-- flyway-mysql 모듈이 BEGIN...END 블록을 인식하므로 DELIMITER 지시자 불필요.
-- =============================================================================

CREATE TRIGGER `trg_audit_logs_no_update`
BEFORE UPDATE ON `audit_logs`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. UPDATE is forbidden.';
END;

CREATE TRIGGER `trg_audit_logs_no_delete`
BEFORE DELETE ON `audit_logs`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. DELETE is forbidden.';
END;
```

- [ ] **Step 2: V3 검증**

```bash
grep -c "^CREATE TRIGGER" backend/butterfly-api/src/main/resources/db/migration/V3__audit_triggers.sql
```
Expected: `2`

- [ ] **Step 3: 커밋**

```bash
git add backend/butterfly-api/src/main/resources/db/migration/V3__audit_triggers.sql
git commit -m "feat(api): V3 audit_triggers 마이그레이션 추가 (audit_logs append-only)"
```

---

## Task 5: 기존 reporting 마이그레이션·원본 SQL 삭제

reporting 모듈에 남은 V1~V13과 원본 schema 파일을 삭제한다. butterfly-api가 reporting 모듈을 의존해도 `db/migration/` 디렉토리가 비어 있으면 클래스패스 중복 없음.

**Files:**
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V1__create_notification_logs.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V2__create_user_action_logs.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V3__create_audit_logs.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V4__create_audit_logs_triggers.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V5__create_send_reconciliations.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V6__create_system_health_snapshots.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V7__create_campaign_stats_hourly.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V8__create_campaign_stats_daily.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V9__create_template_stats_daily.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V10__create_failure_stats_daily.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V11__create_project_stats_daily.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V12__add_reporting_foreign_keys.sql`
- Delete: `backend/butterfly-reporting/src/main/resources/db/migration/V13__add_notification_logs_message_key.sql`
- Delete: `backend/butterfly-reporting/db/schema_analytics_audit.sql`

- [ ] **Step 1: reporting 마이그레이션 디렉토리 통째로 삭제**

```bash
git rm -r backend/butterfly-reporting/src/main/resources/db/migration/
```
Expected: 13개 SQL 파일이 staged for deletion.

- [ ] **Step 2: 원본 schema SQL 삭제**

```bash
git rm backend/butterfly-reporting/db/schema_analytics_audit.sql
```

`backend/butterfly-reporting/db/` 디렉토리가 빈 디렉토리로 남는다. git은 빈 디렉토리를 추적하지 않으므로 커밋 시 자동 제거된다. 명시적 삭제:

```bash
rmdir backend/butterfly-reporting/db 2>/dev/null || true
```

- [ ] **Step 3: 빌드 클래스패스에서 사라졌는지 확인**

```bash
find backend -name "V*__*.sql" | sort
```
Expected: butterfly-api의 V1/V2/V3 3개만 표시.

- [ ] **Step 4: 커밋**

```bash
git commit -m "chore(reporting): 마이그레이션 V1~V13 + 원본 schema_analytics_audit.sql 삭제 (V1~V3로 흡수)"
```

---

## Task 6: 설정 변경 — application.properties + build.gradle 갱신

**Files:**
- Modify: `backend/butterfly-api/src/main/resources/application.properties`
- Modify: `backend/butterfly-worker/src/main/resources/application.properties`
- Modify: `backend/butterfly-api/build.gradle`

- [ ] **Step 1: butterfly-api/application.properties — Flyway 주석 갱신 + ddl-auto 일몰 조건 명시**

`backend/butterfly-api/src/main/resources/application.properties`에서 기존 Flyway 블록을 찾아 교체한다.

기존 블록 (라인 11~22 부근):
```properties
# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.open-in-view=false

# Flyway (ADR-0013) — reporting 모듈의 V*.sql 마이그레이션 적용.
# 다른 모듈은 ddl-auto=update가 만들고, Flyway는 reporting 테이블만 관리하는 하이브리드 모드.
# baseline-on-migrate=true: 기존 DB(다른 모듈이 만든 테이블 포함) 위에서도 안전하게 시작.
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.validate-on-migrate=true
```

교체 후:
```properties
# JPA
# ddl-auto=update는 베이스라인 안정화까지의 한시 정책 (ADR-0013).
# 도메인별 정합성 검증이 모두 끝나면 별도 티켓에서 ddl-auto=validate로 강등한다.
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.open-in-view=false

# Flyway (ADR-0013) — butterfly-api가 베이스라인 V1/V2/V3을 보유.
# V1: core_baseline (B그룹 22개), V2: reporting_baseline (A그룹 10개), V3: audit triggers.
# 빈 DB 가정. baseline-on-migrate=true는 기존 reporting V1~V13 흔적이 남은 환경 호환용.
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.validate-on-migrate=true
```

- [ ] **Step 2: butterfly-worker/application.properties — Flyway 비활성화**

`backend/butterfly-worker/src/main/resources/application.properties`에서 기존 Flyway 블록을 찾아 교체한다.

기존 블록 (라인 13~18 부근):
```properties
# Flyway (ADR-0013) — api와 동일 DB. schema_history 락으로 동시 부팅 시 안전하게 직렬화됨.
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.validate-on-migrate=true
```

교체 후:
```properties
# Flyway 비활성화 — butterfly-api가 마이그레이션 주체 (ADR-0013).
# docker-compose의 depends_on: condition: service_healthy + api healthcheck로
# api 마이그레이션 완료 후 worker 부팅을 보장한다. (api healthcheck 보강은 별도 티켓)
spring.flyway.enabled=false
```

- [ ] **Step 3: butterfly-api/build.gradle — Flyway 의존성 주석 갱신**

`backend/butterfly-api/build.gradle`의 기존 라인 22 부근:
```gradle
    // Flyway (ADR-0013) — DB 마이그레이션. reporting 모듈만 V*.sql 보유, 다른 모듈은 ddl-auto=update 유지.
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'
```

교체 후:
```gradle
    // Flyway (ADR-0013) — butterfly-api가 베이스라인 V1/V2/V3을 보유한다.
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'
```

- [ ] **Step 4: 변경된 파일 확인**

```bash
git diff --stat backend/butterfly-api/src/main/resources/application.properties backend/butterfly-worker/src/main/resources/application.properties backend/butterfly-api/build.gradle
```
Expected: 3개 파일 변경.

- [ ] **Step 5: 커밋**

```bash
git add backend/butterfly-api/src/main/resources/application.properties backend/butterfly-worker/src/main/resources/application.properties backend/butterfly-api/build.gradle
git commit -m "chore(api,worker): Flyway 베이스라인 전환 — worker는 Flyway 비활성화, api 주석 갱신"
```

---

## Task 7: 빌드 검증 — 컴파일 + 단위 테스트

베이스라인 작성 후 빌드가 깨지지 않는지 확인한다 (코드 변경은 없지만 reporting의 SQL 파일 의존성 등 누락 가능성 차단).

**Files:**
- N/A (검증만)

- [ ] **Step 1: Gradle 빌드**

```bash
cd backend && ./gradlew clean build -x test
```
Expected: `BUILD SUCCESSFUL`. 모든 모듈 컴파일 성공.

- [ ] **Step 2: 테스트 실행 (testcontainer 빠르게 검증)**

```bash
cd backend && ./gradlew :butterfly-reporting:test
```
Expected: reporting 모듈 단위 테스트 통과. 기존 테스트가 reporting V*.sql에 직접 의존하면 실패할 수 있다 — 그 경우 다음 Step에서 처리.

- [ ] **Step 3: 실패 시 — reporting 테스트가 마이그레이션 파일에 의존하는지 점검**

```bash
grep -r "V1__create_notification_logs\|V12__add_reporting_foreign_keys\|V13__add_notification_logs_message_key" backend/butterfly-reporting/src/test/ 2>/dev/null
```
Expected: 빈 결과. 만약 매치가 있으면 해당 테스트가 마이그레이션 파일 경로에 직접 의존하므로 코드 수정 필요. 매치 없으면 다음 Step.

- [ ] **Step 4: testcontainer 기반 통합 테스트 점검**

```bash
grep -rl "Testcontainers\|MySQLContainer" backend/butterfly-reporting/src/test/ 2>/dev/null
```
Expected: 통합 테스트 파일 목록. testcontainer는 자동으로 새 V1/V2/V3을 클래스패스에서 찾으므로 그대로 통과해야 한다.

- [ ] **Step 5: 커밋 (Step 2~4가 모두 통과한 경우만)**

이 Task는 검증 only — 변경 없으면 커밋 없음.

만약 Step 3에서 매치가 있어 코드 수정이 필요하면:
```bash
git add <수정한 테스트 파일>
git commit -m "test(reporting): 베이스라인 V1/V2/V3 전환에 맞춰 테스트 경로 정정"
```

---

## Task 8: 로컬 부팅 검증 — DB drop + butterfly-api bootRun

**Files:**
- N/A (검증 only, MySQL 컨테이너 필요)

- [ ] **Step 1: 로컬 MySQL 컨테이너 상태 확인**

```bash
docker ps | grep mysql
```
Expected: butterfly DB가 떠 있어야 한다 (포트 3307). 없으면 docker-compose up으로 띄운다.

- [ ] **Step 2: DB 초기화**

```bash
docker exec -i $(docker ps -qf "ancestor=mysql:8" | head -1) mysql -uroot -proot -e "DROP DATABASE IF EXISTS butterfly; CREATE DATABASE butterfly DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```
Expected: 명령 성공, 출력 없음.

- [ ] **Step 3: butterfly-api 부팅**

```bash
cd backend && ./gradlew :butterfly-api:bootRun
```
Expected 로그:
- `Flyway Community Edition X.Y.Z by Redgate`
- `Successfully validated 3 migrations`
- `Migrating schema "butterfly" to version "1 - core baseline"`
- `Migrating schema "butterfly" to version "2 - reporting baseline"`
- `Migrating schema "butterfly" to version "3 - audit triggers"`
- `Successfully applied 3 migrations to schema "butterfly"`
- 이후 Hibernate `update`가 엔티티 ↔ V1 차이를 보정 (ALTER TABLE 로그 다수 가능 — 기록만)
- 부팅 완료 메시지 (`Started ButterflyApiApplication in X seconds`)

부팅이 성공하면 **Ctrl+C**로 종료.

- [ ] **Step 4: schema_history 확인**

```bash
docker exec -i $(docker ps -qf "ancestor=mysql:8" | head -1) mysql -uroot -proot butterfly -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```
Expected:
```
1  core baseline       1
2  reporting baseline  1
3  audit triggers      1
```

- [ ] **Step 5: 테이블 수 확인**

```bash
docker exec -i $(docker ps -qf "ancestor=mysql:8" | head -1) mysql -uroot -proot butterfly -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='butterfly' AND table_type='BASE TABLE';"
```
Expected: `33` (V1: 22 + V2: 10 + flyway_schema_history: 1 = 33). Hibernate update가 추가한 테이블이 있으면 더 많을 수 있다 — 차이는 Step 7에서 기록.

- [ ] **Step 6: 트리거 확인**

```bash
docker exec -i $(docker ps -qf "ancestor=mysql:8" | head -1) mysql -uroot -proot butterfly -e "SHOW TRIGGERS FROM butterfly LIKE 'audit_logs';"
```
Expected: `trg_audit_logs_no_update`, `trg_audit_logs_no_delete` 두 트리거.

- [ ] **Step 7: audit_logs UPDATE 차단 확인**

```bash
docker exec -i $(docker ps -qf "ancestor=mysql:8" | head -1) mysql -uroot -proot butterfly -e "INSERT INTO audit_logs (member_id, action, target_type, target_id, created_at) VALUES (1, 'TEST', 'test', 1, NOW()); UPDATE audit_logs SET action='HACK' WHERE audit_id=1;" 2>&1
```
Expected: INSERT 성공 후 UPDATE 시 `ERROR 1644 (45000): audit_logs is append-only. UPDATE is forbidden.`

- [ ] **Step 8: Hibernate가 추가/변경한 컬럼 차이 캡처 (정합성 후속 티켓 입력)**

`./gradlew :butterfly-api:bootRun` 로그에서 `Hibernate: alter table` 출력 라인을 모아 기록한다 (텍스트로 저장 — 커밋은 안 함, 후속 티켓 백로그 입력용):

```bash
cd backend && ./gradlew :butterfly-api:bootRun 2>&1 | grep -i "alter table\|create table\|add column\|drop column" > /tmp/hibernate-diff.log
cat /tmp/hibernate-diff.log
```

이 로그는 도메인별 정합성 검증 티켓의 시작점이 된다.

- [ ] **Step 9: 정리 — 부팅 종료 + DB 정리 (선택)**

```bash
# 부팅 종료 (Ctrl+C)
# DB는 다음 작업 위해 그대로 두거나, 깨끗하게 다시 초기화:
docker exec -i $(docker ps -qf "ancestor=mysql:8" | head -1) mysql -uroot -proot -e "DROP DATABASE butterfly; CREATE DATABASE butterfly DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

---

## Task 9: testcontainer 통합 검증

baseline V1/V2/V3을 클린 MySQL 8 testcontainer에서 단독 적용하는 통합 테스트를 작성. 이건 spec §5-12의 검증 단계.

**Files:**
- Create: `backend/butterfly-reporting/src/test/java/com/b101/butterfly/reporting/baseline/BaselineMigrationIntegrationTest.java`

- [ ] **Step 1: 통합 테스트 작성 (failing test)**

`backend/butterfly-reporting/src/test/java/com/b101/butterfly/reporting/baseline/BaselineMigrationIntegrationTest.java` 신규:

```java
package com.b101.butterfly.reporting.baseline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 베이스라인 V1/V2/V3 마이그레이션이 클린 MySQL 8 testcontainer에서 단독 적용되는지 검증한다.
 *
 * - V1: 22개 테이블
 * - V2: 10개 테이블
 * - V3: audit_logs 트리거 2개
 * - flyway_schema_history: 3 row
 */
@SpringBootTest(classes = BaselineMigrationIntegrationTest.TestConfig.class)
@Testcontainers
class BaselineMigrationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("butterfly_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v1_v2_v3_적용_후_테이블_32개_생성() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                "AND table_name <> 'flyway_schema_history'",
                Integer.class, mysql.getDatabaseName());
        assertThat(count).isEqualTo(32);
    }

    @Test
    void schema_history_3개_row_성공_상태() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void audit_logs_트리거_2개_등록() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.triggers " +
                "WHERE trigger_schema = ? AND event_object_table = 'audit_logs'",
                Integer.class, mysql.getDatabaseName());
        assertThat(count).isEqualTo(2);
    }

    @Test
    void audit_logs_UPDATE_차단() {
        jdbc.update("INSERT INTO audit_logs (member_id, action, target_type, created_at) " +
                    "VALUES (1, 'TEST', 'test', NOW())");
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action='HACK' WHERE audit_id=1"))
                .hasMessageContaining("audit_logs is append-only");
    }

    @org.springframework.boot.SpringBootApplication
    static class TestConfig {
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

butterfly-reporting 모듈에는 V*.sql이 더 이상 없으므로, `classpath:db/migration` 경로로 V1/V2/V3을 읽어오려면 butterfly-api 모듈을 의존해야 한다. 그런데 reporting → api 의존은 모듈 의존 방향(api → 도메인)을 거꾸로 하므로 금지된다.

**대안**: 이 통합 테스트를 `butterfly-api` 모듈에 두는 것이 자연스럽다. `backend/butterfly-api/src/test/java/com/b101/butterfly/api/baseline/BaselineMigrationIntegrationTest.java`로 옮긴다.

butterfly-api에 testcontainers 의존성이 없으면 추가 필요:

```bash
grep -i testcontainers backend/butterfly-api/build.gradle
```
Expected: 빈 결과. 없으므로 추가 필요.

- [ ] **Step 3: butterfly-api/build.gradle에 testcontainers 의존성 추가**

`backend/butterfly-api/build.gradle`의 `dependencies` 블록 끝부분, `testRuntimeOnly` 위에 추가:

```gradle
    testImplementation project(':butterfly-testsupport')
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:mysql'
    runtimeOnly 'com.mysql:mysql-connector-j'
```

`runtimeOnly 'com.mysql:mysql-connector-j'`는 testcontainer가 만든 DB에 JDBC로 연결하기 위함.

- [ ] **Step 4: 테스트 위치 옮기기**

```bash
mkdir -p backend/butterfly-api/src/test/java/com/b101/butterfly/api/baseline
mv backend/butterfly-reporting/src/test/java/com/b101/butterfly/reporting/baseline/BaselineMigrationIntegrationTest.java \
   backend/butterfly-api/src/test/java/com/b101/butterfly/api/baseline/BaselineMigrationIntegrationTest.java
rmdir backend/butterfly-reporting/src/test/java/com/b101/butterfly/reporting/baseline 2>/dev/null
```

- [ ] **Step 5: 패키지 선언 수정**

`backend/butterfly-api/src/test/java/com/b101/butterfly/api/baseline/BaselineMigrationIntegrationTest.java`의 첫 라인을 변경:

```java
// 변경 전
package com.b101.butterfly.reporting.baseline;

// 변경 후
package com.b101.butterfly.api.baseline;
```

- [ ] **Step 6: 테스트 실행**

```bash
cd backend && ./gradlew :butterfly-api:test --tests BaselineMigrationIntegrationTest
```
Expected: 4개 테스트 모두 통과.

만약 실패하면:
- 테이블 수가 32가 아닌 경우 → V1/V2 테이블 수 재확인 (Task 2 Step 7, Task 3 Step 7)
- audit_logs 컬럼 누락 → V2 audit_logs 정의 재확인
- testcontainer 시작 실패 → Docker 데몬 상태 확인

- [ ] **Step 7: 커밋**

```bash
git add backend/butterfly-api/src/test/java/com/b101/butterfly/api/baseline/BaselineMigrationIntegrationTest.java backend/butterfly-api/build.gradle
git commit -m "test(api): 베이스라인 V1/V2/V3 testcontainer 통합 테스트 추가"
```

---

## Task 10: 후속 티켓 백로그 정리 + 최종 PR 준비

**Files:**
- N/A (Jira/문서 작업)

- [ ] **Step 1: Hibernate ALTER TABLE 차이 로그 정리**

Task 8 Step 8에서 캡처한 `/tmp/hibernate-diff.log`를 도메인별로 분류해 별도 메모로 정리. 이건 후속 정합성 검증 티켓 8개의 입력이 된다.

도메인별 분류:
- tenant: projects, members, applications
- auth: refresh_tokens, token_blacklist
- event: users, devices, events, event_categories, event_schemas, event_schema_fields, event_daily_agg, user_tags, tag_definitions
- segment: segments, segment_members, segment_favorites, segment_snapshots, segment_snapshot_users
- campaign: campaigns, campaign_executions, message_templates
- notification: (V2 흡수 — notification_logs 등은 reporting 정의 사용)
- reporting: notification_logs, user_action_logs, audit_logs, send_reconciliations, system_health_snapshots, *_stats_*

- [ ] **Step 2: 후속 작업 백로그 등록 (Jira 또는 docs/adr)**

후속 티켓으로 만들어야 할 항목 (spec §9):
1. 도메인별 정합성 검증 8개 (각 도메인 1티켓)
2. 첨부 ERD COMMENT 한글 복원 (V4 또는 도메인별)
3. FK 일괄 추가 (ADR-0013 §5)
4. **모든 도메인 정합성 검증 완료 후**: ddl-auto=update → validate 강등 (api/worker 동시)
5. api healthcheck를 Flyway 완료 후 200 반환하도록 보강
6. ADR-0013 보강 + ADR-0014 신규 (V1/V2/V3 분할 정책 + ddl-auto 일몰 조건)

이 항목들은 jira-my-work 또는 별도 도구로 등록. 본 plan 범위 밖이므로 메모만.

- [ ] **Step 3: 최종 git 상태 확인**

```bash
git log --oneline develop..HEAD
```
Expected: 최소 5개 커밋
1. `feat(api): V1 core_baseline 마이그레이션 추가`
2. `feat(api): V2 reporting_baseline 마이그레이션 추가`
3. `feat(api): V3 audit_triggers 마이그레이션 추가`
4. `chore(reporting): 마이그레이션 V1~V13 + 원본 schema_analytics_audit.sql 삭제`
5. `chore(api,worker): Flyway 베이스라인 전환`
6. `test(api): 베이스라인 V1/V2/V3 testcontainer 통합 테스트 추가`

- [ ] **Step 4: 최종 빌드 한 번 더**

```bash
cd backend && ./gradlew clean build
```
Expected: `BUILD SUCCESSFUL`. 모든 모듈 컴파일 + 테스트 통과 (testcontainer 포함).

- [ ] **Step 5: PR 작성용 메모**

PR 본문에 포함할 내용:
- 변경 요약: V1/V2/V3 베이스라인 신설, reporting V1~V13 흡수·삭제, worker Flyway 비활성화
- 검증: testcontainer 통합 테스트 통과, 로컬 부팅 검증 완료
- 후속 티켓 8개 + 일몰 조건 (ddl-auto=validate) 명시
- spec/plan 링크: `docs/superpowers/specs/2026-05-04-flyway-baseline-v1-design.md`, `docs/superpowers/plans/2026-05-04-flyway-baseline-v1.md`

PR 생성은 사용자 워크플로우에 따라 별도 진행 (이 plan 범위 밖).

---

## 자체 검토 결과 (writing-plans §Self-Review)

**Spec coverage:**
- §4.1 디렉토리 구조 → Task 2/3/4 (V1/V2/V3 생성), Task 5 (reporting 삭제) ✓
- §4.2 V1 22개 테이블 → Task 2 ✓
- §4.3 V2 10개 테이블 → Task 3 ✓
- §4.4 V3 트리거 → Task 4 ✓
- §4.5 정제 규칙 (PK/UK/AUTO_INCREMENT/오타/COMMENT) → Task 1 (사전), Task 2 (적용) ✓
- §4.6 FK 미포함 → 모든 V에 FK 없음 ✓
- §5 구현 단계 12개 → Task 1~9에 분산 ✓
- §6 설정 변경 → Task 6 ✓
- §7 검증 시나리오 7개 → Task 7 (빌드), Task 8 (로컬 부팅, 시나리오 1~5+7), Task 9 (testcontainer, 시나리오 6) ✓

**Placeholder scan:** TBD/TODO 없음. 모든 SQL과 명령어가 실제 실행 가능한 형태로 작성됨 ✓

**Type consistency:**
- `notification_logs.message_key` 컬럼명·인덱스명 V2 Step 1과 reporting V13 일치 ✓
- 트리거 이름 `trg_audit_logs_no_update`/`trg_audit_logs_no_delete` V3 Step 1과 reporting V4 일치 ✓
- `flyway_schema_history` 테이블명 모든 검증 단계에서 동일 ✓
- 복합 PK 컬럼 순서: `event_daily_agg(log_date, project_id, category_id, user_id)` Task 1 §1 = Task 2 Step 4 ✓
- 복합 UK 5종 (Task 1 §4): user_tags, event_categories, tag_definitions, event_schemas, event_schema_fields → Task 2 Step 4/5에서 모두 SQL로 구현 ✓
