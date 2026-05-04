-- =============================================================================
-- V3: audit_logs append-only 트리거 (출처: reporting V4)
-- ADR-0008 §append-only 3중 방어선 — 1차 방어 (실수 방지용)
-- 실질 방어는 후속 V의 DB 권한 분리 (S14P31B101-125)
--
-- flyway-mysql 모듈이 BEGIN...END 블록을 인식하므로 DELIMITER 지시자 불필요.
-- =============================================================================

CREATE TRIGGER `trg_audit_logs_no_update`
BEFORE UPDATE ON `audit_logs`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. UPDATE is forbidden.';
END;

CREATE TRIGGER `trg_audit_logs_no_delete`
BEFORE DELETE ON `audit_logs`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_logs is append-only. DELETE is forbidden.';
END;
