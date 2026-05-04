-- 테스트용 audit_logs append-only 트리거 (운영 V4 마이그레이션과 동일).
-- ddl-auto=create-drop으로 만든 스키마 위에 트리거만 별도 적용한다.

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
