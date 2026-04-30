-- =============================================================================
-- V1: notification_logs (발송 1건 = 1 row)
-- 출처: butterfly-reporting/db/schema_analytics_audit.sql v2.0
-- FK는 V12에서 후분리 (ADR-0013 §5)
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
