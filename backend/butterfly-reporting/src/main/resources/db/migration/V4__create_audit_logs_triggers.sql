-- =============================================================================
-- V4: audit_logs append-only 트리거 (ADR-0008 §append-only 3중 방어선 — 1차 방어)
-- 실수 방지용. 실질 방어는 V13+의 DB 권한 분리(S14P31B101-125).
--
-- Flyway는 `flyway-mysql` 모듈이 MySQL의 BEGIN...END 블록을 인식하므로
-- DELIMITER 지시자 없이 작성. 트리거 본문 내 세미콜론은 BEGIN...END
-- 사이에서 올바르게 처리된다.
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
