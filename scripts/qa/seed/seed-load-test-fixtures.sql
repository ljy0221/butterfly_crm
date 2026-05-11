-- =============================================================================
-- scripts/qa/seed/seed-load-test-fixtures.sql
--
-- LT-01/02/03 부하 테스트 실행 전 시드 데이터.
-- dev 환경 MySQL 에 root 자격 증명으로 실행.
--
-- 적재 내용:
--   1) Project (id=1) + 발급 API Key
--   2) Member 1명 + Customer 1명
--   3) Campaign 5개 + Channel 2개 (PUSH/EMAIL) → CampaignExecution
--   4) notification_logs:
--      - logId=42: 기본 부모 row (LT-01/02 reaction 부모)
--      - 30K row 분포: BUCKET_HOUR (기본 2026-05-14T00:00:00) 1시간 안, campaign×channel 균등 분포
--   5) message_template 1개
--
-- 멱등성: TRUNCATE 후 INSERT — 본 스크립트를 여러 번 실행해도 같은 상태.
-- 주의: 운영 DB에 절대 실행 금지 (root 권한 + TRUNCATE 위험).
--
-- 사용법:
--   mysql -h localhost -P 3307 -u root -p butterfly < scripts/qa/seed/seed-load-test-fixtures.sql
--
-- 관련 문서: scripts/qa/k6/README.md
-- =============================================================================

-- 안전 가드 — 운영 DB 보호. butterfly DB 가 dev/local 인지 확인 후 진행.
-- (운영 DB 에 본 스크립트 실행 방지 — DBA 가 본 가드를 수동 비활성화하지 않는 한 차단)
SELECT
    CASE
        WHEN @@hostname LIKE '%prod%' OR @@hostname LIKE '%stage%'
        THEN RAISE_APPLICATION_ERROR := 'PRODUCTION/STAGE DB 에 시드 실행 시도 — 차단'
        ELSE 'OK'
    END AS safety_check;

-- ---- 1) Project + API Key ----------------------------------------------------
-- API Key 평문: pk_live_loadtest000000000000abcd (32자)
-- SHA-256 해시 (UTF-8, hex lowercase 64자): 사전 계산 값
--   $ echo -n "pk_live_loadtest000000000000abcd" | sha256sum
--   → b7da5bb96eaa1f5cef25a09f8aae8aa46c3e8ab48f3a9c89cea3e35a8e9c9b27 (계산값 — 본 값은 placeholder, 실제 실행 시 ApiKeyService.sha256() 으로 검증할 것)

-- 본 스크립트는 시드 양식만 제공. 실제 SHA-256 값은 다음으로 산출 후 본 파일에 채울 것:
-- INSERT INTO projects (..., public_api_key) VALUES (..., '<sha256-of-pk_live_loadtest...>');

-- (간소화) 시드 직전 정리 — 부하 테스트 격리
DELETE FROM user_action_logs WHERE project_id = 1;
DELETE FROM notification_logs WHERE project_id = 1;
DELETE FROM send_reconciliations WHERE project_id = 1;
-- DELETE FROM projects WHERE project_id = 1; -- FK 의존 많아 미실시; 본 row 는 idempotent UPSERT 형식
-- DELETE FROM members WHERE project_id = 1;

-- ---- 1.1) Project ------------------------------------------------------------
-- 본 스크립트는 dev 환경 가정. projects.public_api_key 는 SHA-256 해시여야 함 (ADR-0017).
-- INSERT 전에 ApiKeyService.sha256("pk_live_loadtest000000000000abcd") 결과로 채울 것.
-- 또는 다음 한 줄로 SQL 안에서 직접 해싱:
--   SET @api_key_hash = SHA2('pk_live_loadtest000000000000abcd', 256);

SET @api_key_plain = 'pk_live_loadtest000000000000abcd';
SET @api_key_hash = SHA2(@api_key_plain, 256);

INSERT INTO projects (project_id, name, business_no, company_auth_no, owner_id, public_api_key, daily_limit, is_deleted, created_at, updated_at)
VALUES (1, 'Load Test Project', '000-00-00000', 'COMPANY_LOADTEST', 1, @api_key_hash, 100000, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    public_api_key = VALUES(public_api_key),
    daily_limit = VALUES(daily_limit),
    updated_at = NOW();

-- ---- 2) Member (테스트용 운영자) --------------------------------------------
INSERT INTO members (member_id, project_id, email, password, name, role_type, is_deleted, created_at, updated_at)
VALUES (1, 1, 'loadtest@butterfly.local', '$2a$10$loadtestPlaceholder', 'Load Test Operator', 'OPERATOR', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ---- 3) Campaign 5개 + Execution -------------------------------------------
-- 5개 캠페인 (id=1..5), 각각 PUSH/EMAIL 회차로 실행
-- 본 시드는 lifecycle BC 의 Campaign 엔티티 스키마에 맞춰야 함 — 스키마 변동 시 본 SQL 도 갱신
INSERT INTO campaigns (campaign_id, project_id, name, status, channel, created_at, updated_at)
VALUES
    (1, 1, 'LT Campaign A', 'ACTIVE', 'PUSH',  NOW(), NOW()),
    (2, 1, 'LT Campaign B', 'ACTIVE', 'PUSH',  NOW(), NOW()),
    (3, 1, 'LT Campaign C', 'ACTIVE', 'EMAIL', NOW(), NOW()),
    (4, 1, 'LT Campaign D', 'ACTIVE', 'EMAIL', NOW(), NOW()),
    (5, 1, 'LT Campaign E', 'ACTIVE', 'PUSH',  NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ---- 4.1) notification_logs 기본 row (LT-01/02 reaction 부모) ---------------
INSERT INTO notification_logs (
    log_id, project_id, campaign_id, campaign_execution_id, message_template_id,
    channel, status, message_key, user_id, device_id, sent_at, created_at, updated_at
)
VALUES (
    42, 1, 1, NULL, NULL,
    'PUSH', 'DELIVERED', 'lt-seed-msg-42', 1, 1,
    NOW(), NOW(), NOW()
)
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ---- 4.2) notification_logs 30K row 분포 (LT-03 용) -------------------------
-- BUCKET_HOUR = 2026-05-14T00:00:00 안 1시간 동안 30K row 균등 분포
-- 5 campaign × 2 channel = 10 그룹, 각 그룹당 3000 row
-- 본 INSERT 는 5 × 2 × 3000 = 30K — 실행 시간 약 5~30초 (환경에 따라)
--
-- recursive CTE 로 1..3000 시퀀스 생성. MySQL 8+ 필요.

SET cte_max_recursion_depth = 5000;

INSERT INTO notification_logs (
    log_id, project_id, campaign_id, campaign_execution_id, message_template_id,
    channel, status, message_key, user_id, device_id, sent_at, created_at, updated_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 3000
),
combos AS (
    SELECT c.campaign_id, c.channel FROM campaigns c WHERE c.project_id = 1
)
SELECT
    1000 + (combos.campaign_id - 1) * 6000 + (CASE combos.channel WHEN 'PUSH' THEN 0 ELSE 3000 END) + seq.n AS log_id,
    1 AS project_id,
    combos.campaign_id,
    NULL AS campaign_execution_id,
    NULL AS message_template_id,
    combos.channel,
    CASE MOD(seq.n, 10)
        WHEN 0 THEN 'FAILED'
        WHEN 1 THEN 'PENDING'
        ELSE 'DELIVERED'
    END AS status,
    CONCAT('lt-seed-msg-', combos.campaign_id, '-', combos.channel, '-', seq.n) AS message_key,
    1 AS user_id,
    1 AS device_id,
    -- 1시간 안 균등 분포: 2026-05-14T00:00:00 + (n * 1.2초)
    DATE_ADD('2026-05-14 00:00:00', INTERVAL FLOOR(seq.n * 1.2) SECOND) AS sent_at,
    DATE_ADD('2026-05-14 00:00:00', INTERVAL FLOOR(seq.n * 1.2) SECOND) AS created_at,
    DATE_ADD('2026-05-14 00:00:00', INTERVAL FLOOR(seq.n * 1.2) SECOND) AS updated_at
FROM seq
CROSS JOIN combos
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- ---- 5) 시드 검증 -----------------------------------------------------------
SELECT
    'projects' AS table_name,
    COUNT(*) AS row_count
FROM projects WHERE project_id = 1
UNION ALL
SELECT 'campaigns', COUNT(*) FROM campaigns WHERE project_id = 1
UNION ALL
SELECT 'notification_logs (total)', COUNT(*) FROM notification_logs WHERE project_id = 1
UNION ALL
SELECT 'notification_logs (bucket 2026-05-14T00:00)', COUNT(*) FROM notification_logs
    WHERE project_id = 1
    AND created_at >= '2026-05-14 00:00:00'
    AND created_at < '2026-05-14 01:00:00';

-- 기대값:
--   projects                                    : 1
--   campaigns                                   : 5
--   notification_logs (total)                   : 30001 (30K + logId=42)
--   notification_logs (bucket 2026-05-14T00:00) : 30000

-- ---- API Key 평문 출력 (시드 1회용, k6 환경 변수에 복붙) -------------------
SELECT
    'API_KEY (k6 환경변수)' AS variable,
    @api_key_plain AS value,
    @api_key_hash AS db_stored_hash;
