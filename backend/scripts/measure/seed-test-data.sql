-- S14P31B101-294 Phase A — 측정용 seed 데이터.
--
-- 적용:
--   docker exec -i butterfly-mysql mysql -ubutterfly -pbutterfly butterfly < scripts/measure/seed-test-data.sql
--
-- 결과:
--   - users 1000명 (project_id=1)
--   - segment_members 1000행 (segment_id=1 에 모두 매핑)
--   - user_tags 100행 (name 변수, latency 측정용)
--   - campaigns 130개 DRAFT (B 100 + E 30, channel=EMAIL, template_id=2)
--
-- 멱등성: 본 스크립트는 멱등성 없음 — 매번 새로 INSERT. 반복 실행 시 reset SQL 먼저:
--   DELETE FROM segment_members WHERE segment_id = 1;
--   DELETE FROM user_tags WHERE project_id = 1;
--   DELETE FROM users WHERE project_id = 1 AND external_id LIKE 'perf-%';
--   DELETE FROM campaigns WHERE campaign_name LIKE 'PerfTest-%';

USE butterfly;
SET cte_max_recursion_depth = 5000;

-- 1) users 1000명
INSERT INTO users (project_id, external_id, email, push_opt_in, signed_up_at, created_at, updated_at)
WITH RECURSIVE nums(n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM nums WHERE n < 1000
)
SELECT 1,
       CONCAT('perf-', LPAD(n, 5, '0')),
       CONCAT('perf', LPAD(n, 5, '0'), '@test.local'),
       1,
       NOW(),
       NOW(),
       NOW()
FROM nums;

-- 2) segment_members 매핑 (segment 1 에 모두)
INSERT INTO segment_members (segment_id, user_id, matched_at)
SELECT 1, u.user_id, NOW()
FROM users u
WHERE u.project_id = 1
  AND u.external_id LIKE 'perf-%'
  AND NOT EXISTS (SELECT 1 FROM segment_members sm WHERE sm.segment_id = 1 AND sm.user_id = u.user_id);

-- 3) user_tags — name 변수만 100명 (latency 측정 부담 줄임)
INSERT INTO user_tags (project_id, user_id, tag_key, tag_value)
SELECT 1, u.user_id, 'name', CONCAT('PerfUser', LPAD(u.user_id, 5, '0'))
FROM users u
WHERE u.project_id = 1 AND u.external_id LIKE 'perf-%'
ORDER BY u.user_id
LIMIT 100;

-- 4) campaigns Throughput 시나리오 B 용 100개 DRAFT
INSERT INTO campaigns
  (project_id, message_template_id, segment_id, member_id,
   campaign_name, campaign_description, campaign_type, channel,
   campaign_status, send_mode, recurring_execution_count, version)
WITH RECURSIVE nums(n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM nums WHERE n < 100
)
SELECT 1, 2, 1, 1,
       CONCAT('PerfTest-B-', LPAD(n, 3, '0')),
       'S14P31B101-294 Phase A baseline 측정 (Throughput)',
       'ONE_TIME',
       'EMAIL',
       'DRAFT',
       'IMMEDIATE',
       0,
       0
FROM nums;

-- 5) campaigns Latency 시나리오 E 용 30개 DRAFT
INSERT INTO campaigns
  (project_id, message_template_id, segment_id, member_id,
   campaign_name, campaign_description, campaign_type, channel,
   campaign_status, send_mode, recurring_execution_count, version)
WITH RECURSIVE nums(n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM nums WHERE n < 30
)
SELECT 1, 2, 1, 1,
       CONCAT('PerfTest-E-', LPAD(n, 3, '0')),
       'S14P31B101-294 Phase A baseline 측정 (Latency)',
       'ONE_TIME',
       'EMAIL',
       'DRAFT',
       'IMMEDIATE',
       0,
       0
FROM nums;

-- 결과 검증
SELECT 'USERS' AS scope, COUNT(*) AS cnt FROM users WHERE project_id = 1 AND external_id LIKE 'perf-%'
UNION ALL
SELECT 'SEGMENT_MEMBERS', COUNT(*) FROM segment_members WHERE segment_id = 1
UNION ALL
SELECT 'USER_TAGS', COUNT(*) FROM user_tags WHERE project_id = 1 AND tag_key = 'name'
UNION ALL
SELECT 'CAMPAIGNS_DRAFT_B', COUNT(*) FROM campaigns WHERE campaign_name LIKE 'PerfTest-B-%'
UNION ALL
SELECT 'CAMPAIGNS_DRAFT_E', COUNT(*) FROM campaigns WHERE campaign_name LIKE 'PerfTest-E-%';

-- 결과:
-- USERS              1000
-- SEGMENT_MEMBERS    1000
-- USER_TAGS          100
-- CAMPAIGNS_DRAFT_B  100
-- CAMPAIGNS_DRAFT_E  30
