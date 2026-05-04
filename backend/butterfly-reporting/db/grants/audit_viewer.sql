-- =============================================================================
-- audit_viewer 계정 생성 및 권한 부여 (S14P31B101-173 / 173-A)
--
-- 적용 시점: 운영 DB 권한 분리 1단계 (선택적 — 감사 조회 전용 계정)
-- 실행 권한: DBA / root
-- 환경: dev, prod 각각 적용
--
-- 용도: OWNER/ADMIN의 감사 로그 조회 화면(/api/v1/audit-logs)이 read-only
--       readonly replica 또는 별도 connection pool에서 본 계정으로 접속하면
--       app_user 계정이 비정상 상황에서도 감사 조회는 영향받지 않는다.
--
-- v1에서는 도입 여부를 운영 단계에서 결정 — 173-B 적용 시 켜거나 미루거나.
-- =============================================================================

CREATE USER IF NOT EXISTS 'audit_viewer'@'%' IDENTIFIED BY '${AUDIT_VIEWER_PASSWORD}';

-- 감사 로그 조회 전용
GRANT SELECT ON butterfly.audit_logs TO 'audit_viewer'@'%';

-- 다른 테이블 접근 금지 (의도적으로 GRANT 하지 않음).
-- audit 조회 화면이 멤버 정보 같은 다른 테이블을 JOIN해야 한다면 별도 분석 필요.

FLUSH PRIVILEGES;
