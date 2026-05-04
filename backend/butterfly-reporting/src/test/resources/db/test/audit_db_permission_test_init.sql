-- =============================================================================
-- 테스트용 init script (S14P31B101-183, 단계 0 CI 통합 테스트).
--
-- Testcontainers MySQL에 root로 적용해서:
--   1. audit_logs 테이블 + append-only 트리거 (V1+V13 / V4 등가)
--   2. app_user 계정 생성 + db/grants/app_user.sql 등가 GRANT
--
-- 운영 db/grants/app_user.sql 과의 차이:
--   - 비밀번호 placeholder ${APP_USER_PASSWORD} 대신 테스트 고정값 사용
--   - audit_logs / notification_logs 두 테이블만 만들어 권한 부족 시나리오 최소 검증
--   - FK 등 운영 마이그레이션은 생략 (권한 검증에 불필요)
--
-- 운영 GRANT 정의가 변경되면 본 파일도 함께 갱신할 것 — 단일 출처는 운영
-- db/grants/app_user.sql 이지만, 테스트는 비밀번호·DB 명 차이로 분리 유지한다.
-- =============================================================================

-- 1) 최소 스키마: audit_logs + 트리거 + notification_logs (대조군)

CREATE TABLE IF NOT EXISTS `audit_logs` (
    `audit_id`    BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`  BIGINT       NULL,
    `member_id`   BIGINT       NOT NULL,
    `action`      VARCHAR(100) NOT NULL,
    `target_type` VARCHAR(50)  NOT NULL,
    `target_id`   BIGINT       NULL,
    `detail`      JSON         NULL,
    `ip_address`  VARCHAR(45)  NULL,
    `user_agent`  VARCHAR(500) NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`audit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notification_logs` (
    `log_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`  BIGINT       NOT NULL,
    `campaign_id` BIGINT       NOT NULL,
    `channel`     VARCHAR(16)  NOT NULL,
    `status`      VARCHAR(16)  NOT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 트리거 — V4 마이그레이션과 동등 (단계 0의 CI 검증은 GRANT 우선이지만
-- 트리거가 도달 전에 access denied가 나는지도 함께 본다)
DROP TRIGGER IF EXISTS `trg_audit_logs_no_update`;
DROP TRIGGER IF EXISTS `trg_audit_logs_no_delete`;

CREATE TRIGGER `trg_audit_logs_no_update`
BEFORE UPDATE ON `audit_logs`
FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. UPDATE is forbidden.';

CREATE TRIGGER `trg_audit_logs_no_delete`
BEFORE DELETE ON `audit_logs`
FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. DELETE is forbidden.';

-- 2) app_user 계정 + GRANT (운영 db/grants/app_user.sql 등가, 비밀번호 평문)

CREATE USER IF NOT EXISTS 'app_user'@'%' IDENTIFIED BY 'test-app-password';

-- audit_logs: INSERT/SELECT only — 본 GRANT가 트리거 도달 전 access denied를 만든다
GRANT INSERT, SELECT ON `test`.`audit_logs` TO 'app_user'@'%';

-- 대조군: notification_logs는 정상 권한 (status 전이 UPDATE 허용)
GRANT SELECT, INSERT, UPDATE ON `test`.`notification_logs` TO 'app_user'@'%';

FLUSH PRIVILEGES;
