-- =============================================================================
-- V5: send_reconciliations (발송 정합성 검증 결과 — 캠페인 회차 × 채널 단위)
-- 매일 새벽 배치 + 관리자 트리거 API에서 INSERT
-- =============================================================================

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
    KEY `idx_send_reconciliations_campaign`     (`campaign_id`, `checked_at`),
    KEY `idx_send_reconciliations_execution`    (`campaign_execution_id`),
    KEY `idx_send_reconciliations_inconsistent` (`is_consistent`, `checked_at`),
    KEY `idx_send_reconciliations_project_time` (`project_id`, `checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='발송 정합성 검증 결과 (캠페인 회차 × 채널 단위)';
