-- =============================================================================
-- V6: system_health_snapshots
-- Kafka lag, worker TPS, API 에러율 등 운영 지표 요약치
-- FK 없음 (외부 의존성 0)
-- =============================================================================

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
