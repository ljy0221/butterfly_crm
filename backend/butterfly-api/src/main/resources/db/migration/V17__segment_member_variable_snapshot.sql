SET @add_variables_json = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE segment_members ADD COLUMN variables_json JSON NULL AFTER expires_at',
        'DO 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'segment_members'
      AND column_name = 'variables_json'
);

PREPARE add_variables_json_stmt FROM @add_variables_json;
EXECUTE add_variables_json_stmt;
DEALLOCATE PREPARE add_variables_json_stmt;

SET @add_variables_captured_at = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE segment_members ADD COLUMN variables_captured_at DATETIME(3) NULL AFTER variables_json',
        'DO 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'segment_members'
      AND column_name = 'variables_captured_at'
);

PREPARE add_variables_captured_at_stmt FROM @add_variables_captured_at;
EXECUTE add_variables_captured_at_stmt;
DEALLOCATE PREPARE add_variables_captured_at_stmt;

CREATE TABLE IF NOT EXISTS segment_variable_stats (
    project_id       BIGINT       NOT NULL,
    segment_id       BIGINT       NOT NULL,
    variable_key     VARCHAR(128) NOT NULL,
    source_type      VARCHAR(32)  NOT NULL,
    available_count  BIGINT       NOT NULL DEFAULT 0,
    missing_count    BIGINT       NOT NULL DEFAULT 0,
    status           VARCHAR(32)  NOT NULL,
    sample_value     VARCHAR(255) NULL,
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (segment_id, variable_key),
    KEY idx_segment_variable_stats_project_segment (project_id, segment_id),
    KEY idx_segment_variable_stats_status (segment_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
