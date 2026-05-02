-- =============================================================================
-- V2: user_action_logs (사용자 반응 로그 — OPEN/CLICK/CONVERT/DISMISS)
-- notification_logs : user_action_logs = 1 : N
-- project_id, campaign_id는 조회 성능용 비정규화 (ADR-0010 §3.1)
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
