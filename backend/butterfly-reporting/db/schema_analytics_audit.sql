-- =============================================================================
-- Butterfly CRM - 분석 / 감사 / 로깅 파트 DDL
-- Owner: 이재영
-- Version: v2.0 (2026-04-28)
-- DBMS: MySQL 8.0+
-- Charset: utf8mb4
--
-- 본 SQL은 이재영 담당 파트(분석/감사/로깅)의 테이블만 포함합니다.
-- 외부 테이블(projects, campaigns, message_templates, members, users, devices,
-- campaign_executions)은 다른 팀원이 관리하며, 본 파일에서는 FK로만 참조합니다.
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- =============================================================================
-- [1] 로깅 계층
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1-1. notification_logs
-- 발송 1건 = 1 row. status 전이는 UPDATE.
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `notification_logs`;
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

    PRIMARY KEY (`log_id`),
    KEY `idx_notification_logs_project_time`    (`project_id`, `created_at`),
    KEY `idx_notification_logs_campaign_status` (`campaign_id`, `status`),
    KEY `idx_notification_logs_execution`       (`campaign_execution_id`),
    KEY `idx_notification_logs_template`        (`message_template_id`),
    KEY `idx_notification_logs_user_time`       (`user_id`, `created_at`),
    KEY `idx_notification_logs_channel_status`  (`channel`, `status`),
    KEY `idx_notification_logs_error`           (`error_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='알림 발송 로그 (발송 1건 = 1 row, 상태 전이는 UPDATE)';


-- -----------------------------------------------------------------------------
-- 1-2. user_action_logs
-- 발송 건에 대한 사용자 반응 (OPEN/CLICK/CONVERT/DISMISS)
-- notification_logs : user_action_logs = 1 : N
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `user_action_logs`;
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


-- =============================================================================
-- [2] 감사 계층
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 2-1. audit_logs
-- append-only: INSERT/SELECT만 허용 (트리거 + DB 권한 분리로 이중 방어)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `audit_logs`;
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


-- append-only 제약 트리거 (실수 방지)
DELIMITER //
DROP TRIGGER IF EXISTS `trg_audit_logs_no_update`//
CREATE TRIGGER `trg_audit_logs_no_update`
BEFORE UPDATE ON `audit_logs`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. UPDATE is forbidden.';
END//

DROP TRIGGER IF EXISTS `trg_audit_logs_no_delete`//
CREATE TRIGGER `trg_audit_logs_no_delete`
BEFORE DELETE ON `audit_logs`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. DELETE is forbidden.';
END//
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 2-2. send_reconciliations
-- 캠페인 회차별 발송 정합성 검증 결과
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `send_reconciliations`;
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
    KEY `idx_send_reconciliations_campaign`    (`campaign_id`, `checked_at`),
    KEY `idx_send_reconciliations_execution`   (`campaign_execution_id`),
    KEY `idx_send_reconciliations_inconsistent` (`is_consistent`, `checked_at`),
    KEY `idx_send_reconciliations_project_time` (`project_id`, `checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='발송 정합성 검증 결과 (캠페인 회차 × 채널 단위)';


-- -----------------------------------------------------------------------------
-- 2-3. system_health_snapshots
-- Kafka lag, worker TPS, API 에러율 등 시스템 헬스 스냅샷
-- 외부 의존성 없는 독립 테이블 (FK 없음)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `system_health_snapshots`;
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


-- =============================================================================
-- [3] 집계 계층 (파생 테이블, UPSERT 기반)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 3-1. campaign_stats_hourly
-- 5분 주기 스트림 집계 워커가 UPSERT
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `campaign_stats_hourly`;
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
    UNIQUE KEY `uk_campaign_stats_hourly_nat`            (`campaign_id`, `channel`, `bucket_hour`),
    KEY `idx_campaign_stats_hourly_project_hour` (`project_id`, `bucket_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='캠페인 시간 단위 집계 (실시간 대시보드용)';


-- -----------------------------------------------------------------------------
-- 3-2. campaign_stats_daily
-- 매일 새벽 배치로 hourly 롤업 후 파생 지표 계산
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `campaign_stats_daily`;
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
    UNIQUE KEY `uk_campaign_stats_daily_nat`            (`campaign_id`, `channel`, `bucket_date`),
    KEY `idx_campaign_stats_daily_project_date` (`project_id`, `bucket_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='캠페인 일 단위 집계 (성과 리포트 API용)';


-- -----------------------------------------------------------------------------
-- 3-3. template_stats_daily
-- 템플릿별 성과 비교 (어떤 제목/내용이 가장 효과적인지)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `template_stats_daily`;
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
    UNIQUE KEY `uk_template_stats_daily_nat`            (`message_template_id`, `channel`, `bucket_date`),
    KEY `idx_template_stats_daily_project_date` (`project_id`, `bucket_date`),
    KEY `idx_template_stats_daily_top`          (`project_id`, `bucket_date`, `open_rate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='템플릿 일 단위 효율 분석';


-- -----------------------------------------------------------------------------
-- 3-4. failure_stats_daily
-- 실패 원인 대시보드용
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `failure_stats_daily`;
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
    UNIQUE KEY `uk_failure_stats_daily_nat`             (`project_id`, `channel`, `error_code`, `bucket_date`),
    KEY `idx_failure_stats_daily_project_date` (`project_id`, `bucket_date`),
    KEY `idx_failure_stats_daily_top`          (`project_id`, `bucket_date`, `failed_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='실패 원인 일 단위 집계';


-- -----------------------------------------------------------------------------
-- 3-5. project_stats_daily
-- 프로젝트 대시보드용 전사 집계
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `project_stats_daily`;
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
    UNIQUE KEY `uk_project_stats_daily_nat`             (`project_id`, `bucket_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='프로젝트 일 단위 집계 (대시보드용)';


-- =============================================================================
-- [4] FOREIGN KEY 제약
-- 외부 테이블(projects/campaigns/등)이 먼저 생성되어 있어야 합니다.
-- =============================================================================

-- 발송 / 사용자 행동
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_project`   FOREIGN KEY (`project_id`)            REFERENCES `projects`(`project_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_campaign`  FOREIGN KEY (`campaign_id`)           REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_execution` FOREIGN KEY (`campaign_execution_id`) REFERENCES `campaign_executions`(`campaign_execution_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_template`  FOREIGN KEY (`message_template_id`)   REFERENCES `message_templates`(`message_template_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_user`      FOREIGN KEY (`user_id`)               REFERENCES `users`(`user_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_device`    FOREIGN KEY (`device_id`)             REFERENCES `devices`(`device_id`);

ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_log`      FOREIGN KEY (`log_id`)      REFERENCES `notification_logs`(`log_id`) ON DELETE RESTRICT;
ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_user`     FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);
ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_device`   FOREIGN KEY (`device_id`)   REFERENCES `devices`(`device_id`);

-- 감사
ALTER TABLE `audit_logs` ADD CONSTRAINT `FK_audit_logs_project` FOREIGN KEY (`project_id`) REFERENCES `projects`(`project_id`);
ALTER TABLE `audit_logs` ADD CONSTRAINT `FK_audit_logs_member`  FOREIGN KEY (`member_id`)  REFERENCES `members`(`member_id`);

-- 정합성
ALTER TABLE `send_reconciliations` ADD CONSTRAINT `FK_send_reconciliations_project`   FOREIGN KEY (`project_id`)            REFERENCES `projects`(`project_id`);
ALTER TABLE `send_reconciliations` ADD CONSTRAINT `FK_send_reconciliations_campaign`  FOREIGN KEY (`campaign_id`)           REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `send_reconciliations` ADD CONSTRAINT `FK_send_reconciliations_execution` FOREIGN KEY (`campaign_execution_id`) REFERENCES `campaign_executions`(`campaign_execution_id`);

-- 집계
ALTER TABLE `campaign_stats_hourly` ADD CONSTRAINT `FK_campaign_stats_hourly_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `campaign_stats_hourly` ADD CONSTRAINT `FK_campaign_stats_hourly_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);

ALTER TABLE `campaign_stats_daily` ADD CONSTRAINT `FK_campaign_stats_daily_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `campaign_stats_daily` ADD CONSTRAINT `FK_campaign_stats_daily_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);

ALTER TABLE `template_stats_daily` ADD CONSTRAINT `FK_template_stats_daily_project`  FOREIGN KEY (`project_id`)          REFERENCES `projects`(`project_id`);
ALTER TABLE `template_stats_daily` ADD CONSTRAINT `FK_template_stats_daily_template` FOREIGN KEY (`message_template_id`) REFERENCES `message_templates`(`message_template_id`);

ALTER TABLE `failure_stats_daily` ADD CONSTRAINT `FK_failure_stats_daily_project` FOREIGN KEY (`project_id`) REFERENCES `projects`(`project_id`);

ALTER TABLE `project_stats_daily` ADD CONSTRAINT `FK_project_stats_daily_project` FOREIGN KEY (`project_id`) REFERENCES `projects`(`project_id`);


SET FOREIGN_KEY_CHECKS = 1;


-- =============================================================================
-- [5] DB 권한 분리 — 운영 적용 가이드
--
-- 본 파일은 v2 스키마의 단일 진실의 원천이지만, GRANT 스크립트는 환경별로 다르게
-- 적용되므로 본 파일에 직접 두지 않는다. 대신 운영 적용용 스크립트는 다음 위치에 있다:
--
--   backend/butterfly-reporting/db/grants/app_user.sql        — 애플리케이션 계정
--   backend/butterfly-reporting/db/grants/audit_viewer.sql    — 감사 조회 전용 계정
--   backend/butterfly-reporting/db/grants/revoke_legacy.sql   — 기존 계정 회수
--   backend/butterfly-reporting/db/grants/README.md           — 적용 절차 안내
--
-- 적용 절차·롤백 계획·통합 테스트 구성은 다음 제안서 참고:
--   backend/docs/proposals/04-audit-logs-db-permission-separation.md
--
-- 관련 Jira: S14P31B101-173 (정착, 173-A) / S14P31B101-183 (운영 적용, 173-B)
-- 관련 ADR : ADR-0008 "Append-only 3중 방어선"
-- =============================================================================
