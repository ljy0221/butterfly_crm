-- =============================================================================
-- V10: failure_stats_daily (실패 원인 TOP N 대시보드용)
-- ADR-0007 — error_code 기준 그룹 바이
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
