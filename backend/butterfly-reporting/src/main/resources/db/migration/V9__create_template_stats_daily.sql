-- =============================================================================
-- V9: template_stats_daily (템플릿 효율 분석 — A/B 비교, 가장 효과적인 제목)
-- ADR-0007 — message_template_id 기준 자연 키
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
