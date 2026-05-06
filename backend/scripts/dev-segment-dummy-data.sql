-- 세그먼트 사용자 추출 로컬 더미 데이터
-- 반복 실행 가능: project_id=9001 범위 데이터만 삭제 후 다시 삽입한다.
-- 기준:
--   project_id = 9001
--   owner/member_id = 9001
--   purchase category_id = 900101
--   segment_id = 901001..901004

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @PROJECT_ID := 9001;
SET @MEMBER_ID := 9001;
SET @PURCHASE_CATEGORY_ID := 900101;
SET @PURCHASE_SCHEMA_ID := 900101;
SET @SEG_FILTERED_MIXED := 901001;
SET @SEG_EVENT_ONLY := 901002;
SET @SEG_TAG_ONLY := 901003;
SET @SEG_DEVICE_USER := 901004;

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM segment_members
WHERE segment_id BETWEEN 901001 AND 901099;

DELETE FROM segment_favorites
WHERE segment_id BETWEEN 901001 AND 901099
   OR member_id = @MEMBER_ID;

DELETE FROM segment_snapshot_users
WHERE snapshot_id IN (
    SELECT snapshot_id FROM segment_snapshots WHERE segment_id BETWEEN 901001 AND 901099
);

DELETE FROM segment_snapshots
WHERE segment_id BETWEEN 901001 AND 901099;

DELETE FROM segments
WHERE project_id = @PROJECT_ID
   OR segment_id BETWEEN 901001 AND 901099;

DELETE FROM events
WHERE project_id = @PROJECT_ID;

DELETE FROM event_daily_agg
WHERE project_id = @PROJECT_ID;

DELETE FROM event_schema_fields
WHERE schema_id IN (
    SELECT schema_id FROM event_schemas WHERE project_id = @PROJECT_ID
);

DELETE FROM event_schemas
WHERE project_id = @PROJECT_ID;

DELETE FROM event_categories
WHERE project_id = @PROJECT_ID;

DELETE FROM user_tags
WHERE project_id = @PROJECT_ID;

DELETE FROM tag_definitions
WHERE project_id = @PROJECT_ID;

DELETE FROM devices
WHERE project_id = @PROJECT_ID;

DELETE FROM users
WHERE project_id = @PROJECT_ID;

DELETE FROM projects
WHERE project_id = @PROJECT_ID;

DELETE FROM members
WHERE member_id = @MEMBER_ID
   OR email = 'segment-demo-owner@butterfly.local';

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO members (
    member_id, project_id, email, password_hash, name, phone, birth_date, role_type, is_deleted
) VALUES (
    @MEMBER_ID,
    @PROJECT_ID,
    'segment-demo-owner@butterfly.local',
    '$2a$10$segmentDummyPasswordHashForLocalOnly',
    '세그먼트더미',
    '01090019001',
    '1995-05-06',
    'OWNER',
    FALSE
);

INSERT INTO projects (
    project_id, name, business_no, company_auth_no, owner_id, public_api_key, daily_limit, plan, is_deleted
) VALUES (
    @PROJECT_ID,
    '세그먼트 더미 프로젝트',
    '900-10-09001',
    'SEGMENT-DUMMY-AUTH-9001',
    @MEMBER_ID,
    'pk_segment_dummy_9001',
    100000,
    'PRO',
    FALSE
);

INSERT INTO tag_definitions (project_id, tag_key, display_name, tag_type)
VALUES
    (@PROJECT_ID, 'region', '지역', 'STRING'),
    (@PROJECT_ID, 'tier', '회원 등급', 'STRING'),
    (@PROJECT_ID, 'gender', '성별', 'STRING');

INSERT INTO event_categories (category_id, project_id, name, type, description)
VALUES
    (@PURCHASE_CATEGORY_ID, @PROJECT_ID, 'purchase', 'CUSTOM', '더미 구매 이벤트');

INSERT INTO event_schemas (
    schema_id, project_id, category_id, version, schema_def, effective_from, is_current, created_by
) VALUES (
    @PURCHASE_SCHEMA_ID,
    @PROJECT_ID,
    @PURCHASE_CATEGORY_ID,
    1,
    '{
      "type": "object",
      "properties": {
        "productId": { "type": "string" },
        "color": { "type": "string" },
        "size": { "type": "number" },
        "price": { "type": "number" }
      },
      "required": ["productId", "price"]
    }',
    NOW(),
    TRUE,
    @MEMBER_ID
);

INSERT INTO event_schema_fields (
    schema_id, field_name, field_type, is_required, is_promoted, previous_names
) VALUES
    (@PURCHASE_SCHEMA_ID, 'productId', 'STRING', TRUE, TRUE, NULL),
    (@PURCHASE_SCHEMA_ID, 'color', 'STRING', FALSE, TRUE, NULL),
    (@PURCHASE_SCHEMA_ID, 'size', 'NUMBER', FALSE, TRUE, NULL),
    (@PURCHASE_SCHEMA_ID, 'price', 'NUMBER', TRUE, TRUE, NULL);

INSERT INTO users (
    user_id, project_id, external_id, email, phone, birth_date, push_opt_in, signed_up_at
) VALUES
    (900001, @PROJECT_ID, 'dummy-u001', 'dummy-u001@example.com', '01090010001', '1994-01-10', TRUE,  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 120 DAY), '09:00:00')),
    (900002, @PROJECT_ID, 'dummy-u002', 'dummy-u002@example.com', '01090010002', '1989-02-11', TRUE,  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 100 DAY), '09:00:00')),
    (900003, @PROJECT_ID, 'dummy-u003', 'dummy-u003@example.com', '01090010003', '2001-03-12', TRUE,  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 90 DAY),  '09:00:00')),
    (900004, @PROJECT_ID, 'dummy-u004', 'dummy-u004@example.com', '01090010004', '1992-04-13', TRUE,  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 80 DAY),  '09:00:00')),
    (900005, @PROJECT_ID, 'dummy-u005', 'dummy-u005@example.com', '01090010005', '1998-05-14', TRUE,  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 70 DAY),  '09:00:00')),
    (900006, @PROJECT_ID, 'dummy-u006', 'dummy-u006@example.com', '01090010006', '1997-06-15', TRUE,  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 60 DAY),  '09:00:00')),
    (900007, @PROJECT_ID, 'dummy-u007', 'dummy-u007@example.com', '01090010007', '1988-07-16', FALSE, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 50 DAY),  '09:00:00')),
    (900008, @PROJECT_ID, 'dummy-u008', 'dummy-u008@example.com', '01090010008', '2000-08-17', TRUE,  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 40 DAY),  '09:00:00')),
    (900009, @PROJECT_ID, 'dummy-u009', 'dummy-u009@example.com', '01090010009', '1991-09-18', TRUE,  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 30 DAY),  '09:00:00')),
    (900010, @PROJECT_ID, 'dummy-u010', 'dummy-u010@example.com', '01090010010', '1996-10-19', FALSE, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY),  '09:00:00'));

INSERT INTO devices (
    device_id, project_id, user_id, external_id, platform, push_token, push_token_updated_at,
    token_valid, app_version, os_version, last_active_at
) VALUES
    (910001, @PROJECT_ID, 900001, 'dummy-device-900001', 'ANDROID', 'dummy-token-900001', NOW(), TRUE, '1.2.0', 'Android 15', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '12:00:00')),
    (910002, @PROJECT_ID, 900002, 'dummy-device-900002', 'IOS',     'dummy-token-900002', NOW(), TRUE, '1.2.0', 'iOS 18',     TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '12:00:00')),
    (910003, @PROJECT_ID, 900003, 'dummy-device-900003', 'ANDROID', 'dummy-token-900003', NOW(), TRUE, '1.1.0', 'Android 14', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '12:00:00')),
    (910004, @PROJECT_ID, 900004, 'dummy-device-900004', 'WEB',     'dummy-token-900004', NOW(), TRUE, '1.0.0', 'Chrome',     TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '12:00:00')),
    (910005, @PROJECT_ID, 900005, 'dummy-device-900005', 'ANDROID', 'dummy-token-900005', NOW(), TRUE, '1.2.0', 'Android 15', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY), '12:00:00')),
    (910006, @PROJECT_ID, 900006, 'dummy-device-900006', 'ANDROID', 'dummy-token-900006', NOW(), TRUE, '1.0.0', 'Android 13', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '12:00:00')),
    (910007, @PROJECT_ID, 900007, 'dummy-device-900007', 'IOS',     'dummy-token-900007', NOW(), TRUE, '1.2.0', 'iOS 18',     TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '12:00:00')),
    (910008, @PROJECT_ID, 900008, 'dummy-device-900008', 'ANDROID', 'dummy-token-900008', NOW(), TRUE, '1.2.0', 'Android 15', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '12:00:00')),
    (910009, @PROJECT_ID, 900009, 'dummy-device-900009', 'ANDROID', 'dummy-token-900009', NOW(), TRUE, '1.2.0', 'Android 15', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '12:00:00')),
    (910010, @PROJECT_ID, 900010, 'dummy-device-900010', 'IOS',     'dummy-token-900010', NOW(), TRUE, '1.2.0', 'iOS 18',     TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '12:00:00'));

INSERT INTO user_tags (project_id, user_id, tag_key, tag_value)
VALUES
    (@PROJECT_ID, 900001, 'region', '서울'), (@PROJECT_ID, 900001, 'tier', 'VIP'),    (@PROJECT_ID, 900001, 'gender', 'F'),
    (@PROJECT_ID, 900002, 'region', '서울'), (@PROJECT_ID, 900002, 'tier', 'VVIP'),   (@PROJECT_ID, 900002, 'gender', 'M'),
    (@PROJECT_ID, 900003, 'region', '서울'), (@PROJECT_ID, 900003, 'tier', 'BASIC'),  (@PROJECT_ID, 900003, 'gender', 'F'),
    (@PROJECT_ID, 900004, 'region', '부산'), (@PROJECT_ID, 900004, 'tier', 'VIP'),    (@PROJECT_ID, 900004, 'gender', 'M'),
    (@PROJECT_ID, 900005, 'region', '서울'), (@PROJECT_ID, 900005, 'tier', 'BASIC'),  (@PROJECT_ID, 900005, 'gender', 'F'),
    (@PROJECT_ID, 900006, 'region', '서울'), (@PROJECT_ID, 900006, 'tier', 'BASIC'),  (@PROJECT_ID, 900006, 'gender', 'M'),
    (@PROJECT_ID, 900007, 'region', '경기'), (@PROJECT_ID, 900007, 'tier', 'VIP'),    (@PROJECT_ID, 900007, 'gender', 'F'),
    (@PROJECT_ID, 900008, 'region', '서울'), (@PROJECT_ID, 900008, 'tier', 'BASIC'),  (@PROJECT_ID, 900008, 'gender', 'F'),
    (@PROJECT_ID, 900009, 'region', '서울'), (@PROJECT_ID, 900009, 'tier', 'VIP'),    (@PROJECT_ID, 900009, 'gender', 'M'),
    (@PROJECT_ID, 900010, 'region', '서울'), (@PROJECT_ID, 900010, 'tier', 'BASIC'),  (@PROJECT_ID, 900010, 'gender', 'F');

INSERT INTO events (
    project_id, user_id, category_id, device_id, event_uuid, occurred_at, schema_version, event_data
) VALUES
    -- u001: 필터 조건까지 모두 만족, 최근 30일 3회
    (@PROJECT_ID, 900001, @PURCHASE_CATEGORY_ID, 910001, '90010001-0000-0000-0000-000000000001', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '10:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    (@PROJECT_ID, 900001, @PURCHASE_CATEGORY_ID, 910001, '90010001-0000-0000-0000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '10:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":139000}'),
    (@PROJECT_ID, 900001, @PURCHASE_CATEGORY_ID, 910001, '90010001-0000-0000-0000-000000000003', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '10:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":119000}'),
    -- u002: 필터 조건까지 모두 만족, 최근 30일 4회
    (@PROJECT_ID, 900002, @PURCHASE_CATEGORY_ID, 910002, '90010002-0000-0000-0000-000000000001', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  '11:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":149000}'),
    (@PROJECT_ID, 900002, @PURCHASE_CATEGORY_ID, 910002, '90010002-0000-0000-0000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY),  '11:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":159000}'),
    (@PROJECT_ID, 900002, @PURCHASE_CATEGORY_ID, 910002, '90010002-0000-0000-0000-000000000003', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 15 DAY), '11:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    (@PROJECT_ID, 900002, @PURCHASE_CATEGORY_ID, 910002, '90010002-0000-0000-0000-000000000004', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 25 DAY), '11:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":139000}'),
    -- u003: 서울이지만 2회라 minCount=3 미달
    (@PROJECT_ID, 900003, @PURCHASE_CATEGORY_ID, 910003, '90010003-0000-0000-0000-000000000001', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  '12:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    (@PROJECT_ID, 900003, @PURCHASE_CATEGORY_ID, 910003, '90010003-0000-0000-0000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY),  '12:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    -- u004: 구매 조건은 만족하지만 지역이 부산
    (@PROJECT_ID, 900004, @PURCHASE_CATEGORY_ID, 910004, '90010004-0000-0000-0000-000000000001', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '13:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    (@PROJECT_ID, 900004, @PURCHASE_CATEGORY_ID, 910004, '90010004-0000-0000-0000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 12 DAY), '13:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    (@PROJECT_ID, 900004, @PURCHASE_CATEGORY_ID, 910004, '90010004-0000-0000-0000-000000000003', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 22 DAY), '13:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    -- u005: 구매 3회지만 색상이 흰색
    (@PROJECT_ID, 900005, @PURCHASE_CATEGORY_ID, 910005, '90010005-0000-0000-0000-000000000001', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '14:00:00'), 1, '{"productId":"SHOE-WHITE-260","color":"흰색","size":260,"price":129000}'),
    (@PROJECT_ID, 900005, @PURCHASE_CATEGORY_ID, 910005, '90010005-0000-0000-0000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY),  '14:00:00'), 1, '{"productId":"SHOE-WHITE-260","color":"흰색","size":260,"price":129000}'),
    (@PROJECT_ID, 900005, @PURCHASE_CATEGORY_ID, 910005, '90010005-0000-0000-0000-000000000003', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  '14:00:00'), 1, '{"productId":"SHOE-WHITE-260","color":"흰색","size":260,"price":129000}'),
    -- u006: 최근 30일 1회 + 오래된 1회
    (@PROJECT_ID, 900006, @PURCHASE_CATEGORY_ID, 910006, '90010006-0000-0000-0000-000000000001', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  '15:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    (@PROJECT_ID, 900006, @PURCHASE_CATEGORY_ID, 910006, '90010006-0000-0000-0000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 45 DAY), '15:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    -- u007: 구매 3회지만 지역이 경기, pushOptIn=false
    (@PROJECT_ID, 900007, @PURCHASE_CATEGORY_ID, 910007, '90010007-0000-0000-0000-000000000001', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY),  '16:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    (@PROJECT_ID, 900007, @PURCHASE_CATEGORY_ID, 910007, '90010007-0000-0000-0000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY),  '16:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    (@PROJECT_ID, 900007, @PURCHASE_CATEGORY_ID, 910007, '90010007-0000-0000-0000-000000000003', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY),  '16:00:00'), 1, '{"productId":"SHOE-BLACK-260","color":"검정","size":260,"price":129000}'),
    -- u009: 구매 3회지만 사이즈가 270
    (@PROJECT_ID, 900009, @PURCHASE_CATEGORY_ID, 910009, '90010009-0000-0000-0000-000000000001', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '17:00:00'), 1, '{"productId":"SHOE-BLACK-270","color":"검정","size":270,"price":129000}'),
    (@PROJECT_ID, 900009, @PURCHASE_CATEGORY_ID, 910009, '90010009-0000-0000-0000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '17:00:00'), 1, '{"productId":"SHOE-BLACK-270","color":"검정","size":270,"price":129000}'),
    (@PROJECT_ID, 900009, @PURCHASE_CATEGORY_ID, 910009, '90010009-0000-0000-0000-000000000003', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY),  '17:00:00'), 1, '{"productId":"SHOE-BLACK-270","color":"검정","size":270,"price":129000}'),
    -- u010: 구매 5회지만 상품이 액세서리, pushOptIn=false
    (@PROJECT_ID, 900010, @PURCHASE_CATEGORY_ID, 910010, '90010010-0000-0000-0000-000000000001', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY),  '18:00:00'), 1, '{"productId":"ACC-001","color":"검정","size":260,"price":39000}'),
    (@PROJECT_ID, 900010, @PURCHASE_CATEGORY_ID, 910010, '90010010-0000-0000-0000-000000000002', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY),  '18:00:00'), 1, '{"productId":"ACC-001","color":"검정","size":260,"price":39000}'),
    (@PROJECT_ID, 900010, @PURCHASE_CATEGORY_ID, 910010, '90010010-0000-0000-0000-000000000003', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY),  '18:00:00'), 1, '{"productId":"ACC-001","color":"검정","size":260,"price":39000}'),
    (@PROJECT_ID, 900010, @PURCHASE_CATEGORY_ID, 910010, '90010010-0000-0000-0000-000000000004', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY),  '18:00:00'), 1, '{"productId":"ACC-001","color":"검정","size":260,"price":39000}'),
    (@PROJECT_ID, 900010, @PURCHASE_CATEGORY_ID, 910010, '90010010-0000-0000-0000-000000000005', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY),  '18:00:00'), 1, '{"productId":"ACC-001","color":"검정","size":260,"price":39000}');

INSERT INTO event_daily_agg (
    log_date, project_id, category_id, user_id, event_count, first_at, last_at
)
SELECT
    DATE(occurred_at) AS log_date,
    project_id,
    category_id,
    user_id,
    COUNT(*) AS event_count,
    MIN(occurred_at) AS first_at,
    MAX(occurred_at) AS last_at
FROM events
WHERE project_id = @PROJECT_ID
GROUP BY DATE(occurred_at), project_id, category_id, user_id;

INSERT INTO segments (
    segment_id, project_id, name, description, condition_tree, schema_version,
    estimated_count, estimated_at, last_built_at, build_status, created_by,
    is_deleted, build_token, build_started_at, build_heartbeat_at
) VALUES
    (
        @SEG_FILTERED_MIXED,
        @PROJECT_ID,
        '더미_서울_검정구두_3회',
        '최근 30일 검정 260 구두 3회 이상 구매 + 서울 태그',
        '{
          "logic": "and",
          "conditions": [
            {
              "type": "event",
              "categoryId": 900101,
              "filters": [
                { "key": "productId", "op": "eq", "value": "SHOE-BLACK-260" },
                { "key": "color", "op": "eq", "value": "검정" },
                { "key": "size", "op": "eq", "value": 260 },
                { "key": "price", "op": "gte", "value": 100000 }
              ],
              "period": { "type": "relative", "days": 30 },
              "minCount": 3
            },
            { "type": "tag", "key": "region", "op": "eq", "value": "서울" }
          ]
        }',
        4,
        NULL,
        NULL,
        NULL,
        'PENDING',
        @MEMBER_ID,
        FALSE,
        NULL,
        NULL,
        NULL
    ),
    (
        @SEG_EVENT_ONLY,
        @PROJECT_ID,
        '더미_최근구매_3회',
        '최근 30일 purchase 이벤트 3회 이상',
        '{
          "logic": "and",
          "conditions": [
            {
              "type": "event",
              "categoryId": 900101,
              "period": { "type": "relative", "days": 30 },
              "minCount": 3
            }
          ]
        }',
        4,
        NULL,
        NULL,
        NULL,
        'PENDING',
        @MEMBER_ID,
        FALSE,
        NULL,
        NULL,
        NULL
    ),
    (
        @SEG_TAG_ONLY,
        @PROJECT_ID,
        '더미_서울유저',
        'region=서울 태그 유저',
        '{
          "logic": "and",
          "conditions": [
            { "type": "tag", "key": "region", "op": "eq", "value": "서울" }
          ]
        }',
        4,
        NULL,
        NULL,
        NULL,
        'PENDING',
        @MEMBER_ID,
        FALSE,
        NULL,
        NULL,
        NULL
    ),
    (
        @SEG_DEVICE_USER,
        @PROJECT_ID,
        '더미_안드로이드_푸시동의',
        'Android 디바이스 + pushOptIn=true',
        '{
          "logic": "and",
          "conditions": [
            { "type": "device", "key": "platform", "op": "eq", "value": "ANDROID" },
            { "type": "user", "key": "pushOptIn", "op": "eq", "value": true }
          ]
        }',
        4,
        NULL,
        NULL,
        NULL,
        'PENDING',
        @MEMBER_ID,
        FALSE,
        NULL,
        NULL,
        NULL
    );

SELECT '세그먼트 빌드용 더미 데이터 삽입 완료' AS message;

SELECT
    s.segment_id,
    s.name,
    s.build_status,
    s.estimated_count,
    COUNT(sm.user_id) AS member_count_before_rebuild
FROM segments s
LEFT JOIN segment_members sm ON sm.segment_id = s.segment_id
WHERE s.project_id = @PROJECT_ID
GROUP BY s.segment_id, s.name, s.build_status, s.estimated_count
ORDER BY s.segment_id;

SELECT
    'expected_after_rebuild' AS kind,
    @SEG_FILTERED_MIXED AS segment_id,
    '더미_서울_검정구두_3회' AS segment_name,
    '900001,900002' AS expected_user_ids,
    2 AS expected_count
UNION ALL
SELECT 'expected_after_rebuild', @SEG_EVENT_ONLY, '더미_최근구매_3회', '900001,900002,900004,900005,900007,900009,900010', 7
UNION ALL
SELECT 'expected_after_rebuild', @SEG_TAG_ONLY, '더미_서울유저', '900001,900002,900003,900005,900006,900008,900009,900010', 8
UNION ALL
SELECT 'expected_after_rebuild', @SEG_DEVICE_USER, '더미_안드로이드_푸시동의', '900001,900003,900005,900006,900008,900009', 6;
