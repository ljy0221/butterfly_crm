CREATE TABLE IF NOT EXISTS segment_variable_snapshot_stage (
    snapshot_run_id VARCHAR(36) NOT NULL,
    segment_id BIGINT NOT NULL,
    variable_key VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    variable_value VARCHAR(1024) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (snapshot_run_id, user_id),
    KEY idx_segment_variable_snapshot_stage_target (segment_id, variable_key, user_id),
    KEY idx_segment_variable_snapshot_stage_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
