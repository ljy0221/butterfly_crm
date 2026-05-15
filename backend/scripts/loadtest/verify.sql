-- Loadtest verify — 시드/발송 결과 검증 쿼리 모음
--
-- 사용법:
--   sed "s/\${PROJECT_ID}/<your-id>/g" verify.sql \
--     | kubectl exec -i -n butterfly mysql-0 -- mysql -uroot -p"$ROOT_PW" butterfly
--
-- placeholder: ${PROJECT_ID}

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. 시드 적재량 확인
-- ─────────────────────────────────────────────────────────────────────────────
SELECT '1. seed counts' AS section;
SELECT
  (SELECT COUNT(*) FROM users           WHERE project_id = ${PROJECT_ID} AND external_id LIKE 'loadtest-user-%') AS users,
  (SELECT COUNT(*) FROM devices         WHERE project_id = ${PROJECT_ID} AND external_id LIKE 'loadtest-dev-%') AS devices,
  (SELECT COUNT(*) FROM segment_members
    WHERE segment_id IN (SELECT segment_id FROM segments
                          WHERE project_id = ${PROJECT_ID} AND name = 'LoadTest-Segment')) AS segment_members;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. 캠페인 / 실행 회차 상태
-- ─────────────────────────────────────────────────────────────────────────────
SELECT '2. campaign & execution' AS section;
SELECT campaign_id, campaign_status, send_mode, version, created_at, updated_at
  FROM campaigns
 WHERE project_id = ${PROJECT_ID}
   AND campaign_name = 'LoadTest-Campaign-200';

SELECT campaign_execution_id, campaign_id, run_round, campaign_execution_status,
       target_count, send_started_at, send_completed_at, updated_at
  FROM campaign_executions
 WHERE campaign_id IN (SELECT campaign_id FROM campaigns
                        WHERE project_id = ${PROJECT_ID}
                          AND campaign_name = 'LoadTest-Campaign-200')
 ORDER BY campaign_execution_id DESC
 LIMIT 5;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. notification_logs 상태 분포 (가장 중요한 검증 지표)
-- ─────────────────────────────────────────────────────────────────────────────
SELECT '3. notification_logs status' AS section;
SELECT status, COUNT(*) AS cnt
  FROM notification_logs
 WHERE campaign_id IN (SELECT campaign_id FROM campaigns
                        WHERE project_id = ${PROJECT_ID}
                          AND campaign_name = 'LoadTest-Campaign-200')
 GROUP BY status;

-- 합계는 200 이어야 함 (실 토큰 2건 SENT/DELIVERED + 가짜 198건 FAILED 예상)
SELECT '3b. total log count' AS section;
SELECT COUNT(*) AS total_logs
  FROM notification_logs
 WHERE campaign_id IN (SELECT campaign_id FROM campaigns
                        WHERE project_id = ${PROJECT_ID}
                          AND campaign_name = 'LoadTest-Campaign-200');

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. 실 토큰 2건 발송 결과 (loadtest-dev-0001 / 0002)
-- ─────────────────────────────────────────────────────────────────────────────
SELECT '4. real device delivery (5556/5558)' AS section;
SELECT nl.log_id, nl.user_id, nl.device_id, nl.status, nl.error_code,
       nl.sent_at, nl.delivered_at, d.external_id, LEFT(d.push_token, 30) AS token_prefix
  FROM notification_logs nl
  JOIN devices d ON d.device_id = nl.device_id
 WHERE nl.campaign_id IN (SELECT campaign_id FROM campaigns
                           WHERE project_id = ${PROJECT_ID}
                             AND campaign_name = 'LoadTest-Campaign-200')
   AND d.external_id IN ('loadtest-dev-0001', 'loadtest-dev-0002');

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. 실패 에러 코드 TOP — 가짜 토큰들의 FCM 응답 패턴
-- ─────────────────────────────────────────────────────────────────────────────
SELECT '5. failure error codes' AS section;
SELECT error_code, COUNT(*) AS cnt, MAX(error_message) AS sample_message
  FROM notification_logs
 WHERE campaign_id IN (SELECT campaign_id FROM campaigns
                        WHERE project_id = ${PROJECT_ID}
                          AND campaign_name = 'LoadTest-Campaign-200')
   AND status = 'FAILED'
 GROUP BY error_code
 ORDER BY cnt DESC;
