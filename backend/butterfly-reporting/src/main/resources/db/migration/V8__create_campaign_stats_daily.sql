-- =============================================================================
-- V8: campaign_stats_daily (매일 새벽 배치 — hourly 롤업 + 비율 지표)
-- ADR-0007: open_rate 분모는 delivered, click_rate 분모는 open
-- 분모가 0인 경우 NULL (비율 컬럼이 NULL 허용)
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
