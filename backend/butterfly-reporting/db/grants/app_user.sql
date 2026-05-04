-- =============================================================================
-- app_user 계정 생성 및 권한 부여 (S14P31B101-173 / 173-A)
--
-- 적용 시점: 운영 DB 권한 분리 1단계 (새 계정 도입)
-- 실행 권한: DBA / root
-- 환경: dev, prod 각각 적용
--
-- 비밀번호는 시크릿 매니저에서 주입 — 본 파일에 평문 비밀번호를 적지 않는다.
-- 환경 변수 ${APP_USER_PASSWORD}로 envsubst 처리해 사용.
--
-- audit_logs는 INSERT/SELECT만 부여한다 — UPDATE/DELETE는 트리거(V4) +
-- @Immutable 엔티티 + 본 GRANT의 3중 방어선으로 차단된다 (ADR-0008).
-- =============================================================================

CREATE USER IF NOT EXISTS 'app_user'@'%' IDENTIFIED BY '${APP_USER_PASSWORD}';

-- audit_logs: INSERT/SELECT only (append-only 보호 ②)
GRANT INSERT, SELECT ON butterfly.audit_logs TO 'app_user'@'%';

-- 발송/사용자 행동 (status 전이 UPDATE 허용)
GRANT SELECT, INSERT, UPDATE ON butterfly.notification_logs       TO 'app_user'@'%';
GRANT SELECT, INSERT         ON butterfly.user_action_logs        TO 'app_user'@'%';

-- 정합성 검증 (재실행 시 UPDATE 필요)
GRANT SELECT, INSERT, UPDATE ON butterfly.send_reconciliations    TO 'app_user'@'%';

-- 시스템 헬스 (스냅샷 INSERT만)
GRANT SELECT, INSERT         ON butterfly.system_health_snapshots TO 'app_user'@'%';

-- 집계 테이블 5종 (UPSERT 운영 — UPDATE 필요)
GRANT SELECT, INSERT, UPDATE ON butterfly.campaign_stats_hourly   TO 'app_user'@'%';
GRANT SELECT, INSERT, UPDATE ON butterfly.campaign_stats_daily    TO 'app_user'@'%';
GRANT SELECT, INSERT, UPDATE ON butterfly.template_stats_daily    TO 'app_user'@'%';
GRANT SELECT, INSERT, UPDATE ON butterfly.failure_stats_daily     TO 'app_user'@'%';
GRANT SELECT, INSERT, UPDATE ON butterfly.project_stats_daily     TO 'app_user'@'%';

-- 외부 팀 테이블 권한은 별도 모듈에서 관리 (해당 모듈의 db/grants/ 디렉토리 참고).
-- reporting 모듈은 본 모듈의 테이블에 대한 권한만 책임진다.

FLUSH PRIVILEGES;
