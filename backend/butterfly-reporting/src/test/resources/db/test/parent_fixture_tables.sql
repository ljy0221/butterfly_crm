-- =============================================================================
-- 테스트 전용 부모 픽스처 테이블 DDL
--
-- butterfly-reporting 통합 테스트는 ddl-auto=create-drop + Flyway 비활성화로
-- reporting 도메인 엔티티만 Hibernate가 생성한다.
-- V21 FK 추가 이후 notification_logs 등이 projects/members/campaigns를 참조하므로
-- 해당 부모 테이블을 테스트 스키마에도 미리 생성해야 한다.
--
-- 출처: V1__core_baseline.sql + V13__add_member_status.sql + V14 (plural 컬럼 제거)
-- =============================================================================

CREATE TABLE IF NOT EXISTS `projects` (
    `project_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `name`            VARCHAR(255) NOT NULL,
    `business_no`     VARCHAR(50)  NOT NULL,
    `company_auth_no` VARCHAR(100) NOT NULL,
    `owner_id`        BIGINT       NOT NULL,
    `public_api_key`  VARCHAR(100) NULL,
    `daily_limit`     INT          NULL,
    `plan`            ENUM('FREE','BASIC','PRO','ENTERPRISE') NOT NULL DEFAULT 'FREE',
    `is_deleted`      BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted_at`      DATETIME     NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `members` (
    `member_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`     BIGINT       NULL,
    `email`          VARCHAR(255) NOT NULL,
    `password_hash`  VARCHAR(255) NULL,
    `name`           VARCHAR(20)  NOT NULL,
    `phone`          VARCHAR(11)  NOT NULL,
    `birth_date`     DATE         NOT NULL,
    `role_type`      ENUM('ADMIN','OWNER','EDITOR','VIEWER') NOT NULL DEFAULT 'VIEWER',
    `member_status`  VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    `is_deleted`     BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted_at`     DATETIME     NULL,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`member_id`),
    UNIQUE KEY `uk_members_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `message_templates` (
    `message_template_id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `member_id`                    BIGINT       NOT NULL,
    `project_id`                   BIGINT       NOT NULL,
    `message_template_name`        VARCHAR(255) NOT NULL,
    `message_template_description` VARCHAR(500) NOT NULL,
    `channel`                      ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL DEFAULT 'PUSH',
    `message_template_title`       VARCHAR(255) NOT NULL,
    `message_template_body`        TEXT         NOT NULL,
    `message_template_image_url`   VARCHAR(500) NULL,
    `required_variables`           JSON         NOT NULL,
    `is_deleted`                   BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted_at`                   DATETIME     NULL,
    `created_at`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`message_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `campaigns` (
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
    PRIMARY KEY (`campaign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
