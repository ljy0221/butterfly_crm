-- Loadtest cleanup — 부하 테스트로 적재한 모든 데이터 안전 삭제
--
-- 안전 가드: prefix / 고정 name 조건으로만 DELETE. 운영 데이터는 절대 영향 받지 않음.
--   - external_id LIKE 'loadtest-user-%'   (user)
--   - external_id LIKE 'loadtest-dev-%'    (device)
--   - name = 'LoadTest-Segment'            (segment)
--   - campaign_name = 'LoadTest-Campaign-200'
--   - message_template_name = 'LoadTest-Template'
--
-- placeholder: ${PROJECT_ID}

SET FOREIGN_KEY_CHECKS = 0;

-- 1. notification_logs — 해당 캠페인 발송 로그 (가장 많은 row, 먼저 제거)
DELETE FROM notification_logs
 WHERE campaign_id IN (
   SELECT campaign_id FROM campaigns
    WHERE project_id = ${PROJECT_ID}
      AND campaign_name = 'LoadTest-Campaign-200'
 );

-- 2. campaign_executions
DELETE FROM campaign_executions
 WHERE campaign_id IN (
   SELECT campaign_id FROM campaigns
    WHERE project_id = ${PROJECT_ID}
      AND campaign_name = 'LoadTest-Campaign-200'
 );

-- 3. campaigns
DELETE FROM campaigns
 WHERE project_id = ${PROJECT_ID}
   AND campaign_name = 'LoadTest-Campaign-200';

-- 4. message_templates
DELETE FROM message_templates
 WHERE project_id = ${PROJECT_ID}
   AND message_template_name = 'LoadTest-Template';

-- 5. segment_members
DELETE FROM segment_members
 WHERE segment_id IN (
   SELECT segment_id FROM segments
    WHERE project_id = ${PROJECT_ID}
      AND name = 'LoadTest-Segment'
 );

-- 6. segments
DELETE FROM segments
 WHERE project_id = ${PROJECT_ID}
   AND name = 'LoadTest-Segment';

-- 7. devices (loadtest prefix만)
DELETE FROM devices
 WHERE project_id = ${PROJECT_ID}
   AND external_id LIKE 'loadtest-dev-%';

-- 8. users (loadtest prefix만)
DELETE FROM users
 WHERE project_id = ${PROJECT_ID}
   AND external_id LIKE 'loadtest-user-%';

SET FOREIGN_KEY_CHECKS = 1;

-- 잔존 확인 (모두 0 이어야 함)
SELECT
  (SELECT COUNT(*) FROM users           WHERE project_id = ${PROJECT_ID} AND external_id LIKE 'loadtest-user-%') AS remain_users,
  (SELECT COUNT(*) FROM devices         WHERE project_id = ${PROJECT_ID} AND external_id LIKE 'loadtest-dev-%')  AS remain_devices,
  (SELECT COUNT(*) FROM segments        WHERE project_id = ${PROJECT_ID} AND name = 'LoadTest-Segment')          AS remain_segments,
  (SELECT COUNT(*) FROM campaigns       WHERE project_id = ${PROJECT_ID} AND campaign_name = 'LoadTest-Campaign-200') AS remain_campaigns,
  (SELECT COUNT(*) FROM message_templates WHERE project_id = ${PROJECT_ID} AND message_template_name = 'LoadTest-Template') AS remain_templates;
