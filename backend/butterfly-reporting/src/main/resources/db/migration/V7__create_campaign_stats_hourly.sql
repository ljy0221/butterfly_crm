-- =============================================================================
-- V7: campaign_stats_hourly (5분 주기 스트림 집계 — UPSERT)
-- ADR-0007: 자연 키 (campaign_id, channel, bucket_hour) 기반 멱등성
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
