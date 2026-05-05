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

-- -----------------------------------------------------------------------------
-- Event 도메인 (users, devices, event_categories, events, event_schemas,
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
