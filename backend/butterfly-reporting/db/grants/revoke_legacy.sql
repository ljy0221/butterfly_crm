-- =============================================================================
-- 기존 butterfly 계정 권한 회수 (S14P31B101-173 / 173-A)
--
-- 적용 시점: 운영 DB 권한 분리의 마지막 단계
--   - app_user.sql + (선택) audit_viewer.sql 적용 완료
--   - dev/prod 모두 datasource user가 app_user로 전환 완료
--   - 일정 기간 모니터링으로 정상 동작 확인 (제안서 §"적용 절차" 참고)
--
-- 본 스크립트 적용 후에는 기존 butterfly 계정으로 운영 DB에 접속할 수 없다.
-- 적용 전 반드시 새 계정 전환이 100% 끝났는지 확인할 것.
--
-- 실행 권한: DBA / root
-- =============================================================================

-- butterfly 계정의 audit_logs 권한 전체 회수 — 외부 침해 시에도 audit 변조 불가
REVOKE ALL PRIVILEGES ON butterfly.audit_logs FROM 'butterfly'@'%';

-- 다른 테이블의 권한도 회수해 계정 자체를 비활성화에 가깝게 둔다.
-- 단, DROP USER는 하지 않는다 — 다른 환경/스크립트에서 참조될 가능성 + 롤백 여지.
REVOKE ALL PRIVILEGES ON butterfly.notification_logs FROM 'butterfly'@'%';
REVOKE ALL PRIVILEGES ON butterfly.user_action_logs FROM 'butterfly'@'%';
REVOKE ALL PRIVILEGES ON butterfly.send_reconciliations FROM 'butterfly'@'%';
REVOKE ALL PRIVILEGES ON butterfly.system_health_snapshots FROM 'butterfly'@'%';
REVOKE ALL PRIVILEGES ON butterfly.campaign_stats_hourly FROM 'butterfly'@'%';
REVOKE ALL PRIVILEGES ON butterfly.campaign_stats_daily FROM 'butterfly'@'%';
REVOKE ALL PRIVILEGES ON butterfly.template_stats_daily FROM 'butterfly'@'%';
REVOKE ALL PRIVILEGES ON butterfly.failure_stats_daily FROM 'butterfly'@'%';
REVOKE ALL PRIVILEGES ON butterfly.project_stats_daily FROM 'butterfly'@'%';

-- 다른 모듈(외부 팀) 테이블은 본 스크립트 책임 외 — 해당 모듈의 db/grants/ 참고.

FLUSH PRIVILEGES;
