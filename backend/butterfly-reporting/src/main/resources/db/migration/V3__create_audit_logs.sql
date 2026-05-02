-- =============================================================================
-- V3: audit_logs (감사 로그 — append-only)
-- ADR-0008/0009 — reporting 모듈 소유. AuditLog 엔티티와 매핑 일치 필수.
-- INSERT/SELECT만 허용. UPDATE/DELETE는 V4 트리거로 차단.
-- DB 권한 분리는 별도 티켓(S14P31B101-125)에서 V13+로 적용.
-- =============================================================================

CREATE TABLE `audit_logs` (
    `audit_id`     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '감사 로그 ID',
    `project_id`   BIGINT       NULL                     COMMENT '프로젝트 ID (시스템 레벨 시 NULL)',
    `member_id`    BIGINT       NOT NULL                 COMMENT '작업 수행 회원 ID',
    `action`       VARCHAR(100) NOT NULL                 COMMENT '작업 종류 (FCM_KEY_UPDATE, API_KEY_REISSUE 등)',
    `target_type`  VARCHAR(50)  NOT NULL                 COMMENT '대상 엔티티 타입',
    `target_id`    BIGINT       NULL                     COMMENT '대상 엔티티 ID',
    `detail`       JSON         NULL                     COMMENT '추가 상세 (변경 전/후 값 등)',
    `ip_address`   VARCHAR(45)  NULL                     COMMENT '요청 IP (IPv6 대응)',
    `user_agent`   VARCHAR(500) NULL                     COMMENT 'User-Agent',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작업 시각',

    PRIMARY KEY (`audit_id`),
    KEY `idx_audit_logs_project_action` (`project_id`, `action`, `created_at`),
    KEY `idx_audit_logs_actor`          (`member_id`, `created_at`),
    KEY `idx_audit_logs_target`         (`target_type`, `target_id`),
    KEY `idx_audit_logs_action_time`    (`action`, `created_at`),
    KEY `idx_audit_logs_created_at`     (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='감사 로그 (append-only: INSERT/SELECT만 허용)';
