-- =============================================================================
-- V11: project_stats_daily (프로젝트 메인 대시보드용 전사 집계)
-- ADR-0007 — 모든 채널 합산
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
