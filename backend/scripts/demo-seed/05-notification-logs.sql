-- 5/22 시연용 — 시연 5.11 단계 FAILED 필터 데모용 notification_logs 시드
-- 캠페인 105(쿠폰 만료 알림) + 106(리뷰 요청)에 DELIVERED/FAILED 혼합 적재
--
-- 스키마 근거: V2__reporting_baseline.sql
--   status   ENUM('PENDING','SENT','DELIVERED','FAILED')
--   channel  ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO')
--   failure  error_code VARCHAR(50) + error_message TEXT (failure_reason 컬럼 없음)
--   message_key UNIQUE — 멱등 INSERT를 위해 결정적 키 부여

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM notification_logs WHERE project_id = 1;

-- ────────────────────────────────────────────────────────────────────────────
-- 캠페인 105 — 쿠폰 만료 알림 : DELIVERED 10건 + FAILED 3건(INVALID_TOKEN)
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO notification_logs
    (project_id, campaign_id, campaign_execution_id, user_id, channel, status,
     sent_at, delivered_at, created_at, message_key)
SELECT
    1,
    105,
    1,
    200 + seq.n,
    'PUSH',
    'DELIVERED',
    NOW() - INTERVAL 8 DAY + INTERVAL (seq.n * 6) SECOND,
    NOW() - INTERVAL 8 DAY + INTERVAL (seq.n * 6 + 1) SECOND,
    NOW() - INTERVAL 8 DAY,
    CONCAT('demo-105-delivered-', seq.n)
FROM (
    SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) seq;

-- 캠페인 105 FAILED 3건 (시연 5.11 FAILED 필터 화면)
INSERT INTO notification_logs
    (project_id, campaign_id, campaign_execution_id, user_id, channel, status,
     error_code, error_message, sent_at, created_at, message_key)
VALUES
(1, 105, 1, 300, 'PUSH', 'FAILED', 'INVALID_TOKEN', 'FCM registration token is not valid',
 NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY, 'demo-105-failed-1'),
(1, 105, 1, 301, 'PUSH', 'FAILED', 'INVALID_TOKEN', 'FCM registration token is not valid',
 NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY, 'demo-105-failed-2'),
(1, 105, 1, 302, 'PUSH', 'FAILED', 'INVALID_TOKEN', 'FCM registration token is not valid',
 NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY, 'demo-105-failed-3');

-- ────────────────────────────────────────────────────────────────────────────
-- 캠페인 106 — 리뷰 요청 : DELIVERED 10건 + FAILED 2건(NETWORK, RATE_LIMIT)
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO notification_logs
    (project_id, campaign_id, campaign_execution_id, user_id, channel, status,
     sent_at, delivered_at, created_at, message_key)
SELECT
    1,
    106,
    1,
    400 + seq.n,
    'PUSH',
    'DELIVERED',
    NOW() - INTERVAL 3 DAY + INTERVAL (seq.n * 6) SECOND,
    NOW() - INTERVAL 3 DAY + INTERVAL (seq.n * 6 + 1) SECOND,
    NOW() - INTERVAL 3 DAY,
    CONCAT('demo-106-delivered-', seq.n)
FROM (
    SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) seq;

-- 캠페인 106 FAILED 2건
INSERT INTO notification_logs
    (project_id, campaign_id, campaign_execution_id, user_id, channel, status,
     error_code, error_message, sent_at, created_at, message_key)
VALUES
(1, 106, 1, 500, 'PUSH', 'FAILED', 'NETWORK', 'Connection timed out while contacting FCM',
 NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, 'demo-106-failed-1'),
(1, 106, 1, 501, 'PUSH', 'FAILED', 'RATE_LIMIT', 'FCM quota exceeded for this project',
 NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, 'demo-106-failed-2');

SET FOREIGN_KEY_CHECKS = 1;
