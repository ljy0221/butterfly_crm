-- 광고 영상 스크린샷용 데모 데이터
-- 범위: project_id=1 로컬 시연 데이터만 재구성한다.

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM user_action_logs WHERE project_id = 1;
DELETE FROM notification_logs WHERE project_id = 1;
DELETE FROM campaign_stats_daily WHERE project_id = 1;
DELETE FROM project_stats_daily WHERE project_id = 1;
DELETE FROM template_stats_daily WHERE project_id = 1;
DELETE FROM failure_stats_daily WHERE project_id = 1;
DELETE FROM campaign_stats WHERE campaign_id IN (SELECT campaign_id FROM campaigns WHERE project_id = 1);
DELETE FROM campaign_executions WHERE campaign_id IN (SELECT campaign_id FROM campaigns WHERE project_id = 1);
DELETE FROM campaigns WHERE project_id = 1;
DELETE FROM message_templates WHERE project_id = 1;
DELETE FROM segment_members WHERE segment_id IN (SELECT segment_id FROM segments WHERE project_id = 1);
DELETE FROM segment_favorites WHERE segment_id IN (SELECT segment_id FROM segments WHERE project_id = 1);
DELETE FROM segments WHERE project_id = 1;
DELETE FROM event_daily_agg WHERE project_id = 1;
DELETE FROM events WHERE project_id = 1;
DELETE FROM event_schema_fields WHERE schema_id IN (SELECT schema_id FROM event_schemas WHERE project_id = 1);
DELETE FROM event_schemas WHERE project_id = 1;
DELETE FROM event_categories WHERE project_id = 1;
DELETE FROM user_tags WHERE project_id = 1;
DELETE FROM devices WHERE project_id = 1;
DELETE FROM devices WHERE device_id BETWEEN 2001 AND 2008;
DELETE FROM users WHERE project_id = 1;
DELETE FROM users WHERE user_id BETWEEN 1001 AND 1008;
DELETE FROM credit_transaction WHERE project_id = 1;
DELETE FROM credit_transaction WHERE credit_transaction_id IN (1, 2);
DELETE FROM credit_reservation WHERE project_id = 1;
DELETE FROM credit_account WHERE project_id = 1;
DELETE FROM audit_logs WHERE project_id = 1;
DELETE FROM members WHERE project_id = 1;
DELETE FROM members WHERE member_id = 1;
DELETE FROM projects WHERE project_id = 1;

INSERT INTO projects (
    project_id, name, business_no, company_auth_no, owner_id,
    public_api_key, daily_limit, plan, is_deleted, created_at, updated_at
) VALUES (
    1, 'Butterfly Shoes', '101-01-00001', 'storyboard-demo', 1,
    '6d347da9e002fc62e18eee9fb6363cf4b8d05a6d1399e7b5324bc261053c79f1',
    100000, 'PRO', FALSE, NOW(), NOW()
);

INSERT INTO members (
    member_id, project_id, email, password_hash, name, phone, birth_date,
    role_type, member_status, is_deleted, created_at, updated_at
) VALUES (
    1, 1, 'demo@butterfly.io',
    '$2a$10$b5Dduy0VCx8hF50KE720wuYJQ/WT2nDmMvMHFeazxTIzw2whgc6de',
    '데모운영자', '01000000000', '1990-01-01',
    'OWNER', 'ACTIVE', FALSE, NOW(), NOW()
);

INSERT INTO credit_account (project_id, balance, reserved_balance, version, created_at, updated_at)
VALUES (1, 850000, 127000, 0, NOW(), NOW());

INSERT INTO credit_transaction (
    credit_transaction_id, project_id, payment_order_id, reference_type, reference_id,
    delta, reason, balance_after, memo, created_at, updated_at
) VALUES
    (1, 1, NULL, NULL, NULL, 1000000, 'CHARGE', 1000000, '광고 영상 데모 크레딧 충전', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY)),
    (2, 1, NULL, 'CAMPAIGN_EXECUTION', 601, -150000, 'SPEND', 850000, '리타게팅 캠페인 발송 예약', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO users (user_id, project_id, external_id, email, phone, birth_date, push_opt_in, signed_up_at, created_at, updated_at)
VALUES
    (1001, 1, 'park-junho', 'junho@example.com', '01010001001', '1995-03-12', TRUE, DATE_SUB(NOW(), INTERVAL 120 DAY), NOW(), NOW()),
    (1002, 1, 'lee-jieun', 'jieun@example.com', '01010001002', '1998-07-02', TRUE, DATE_SUB(NOW(), INTERVAL 94 DAY), NOW(), NOW()),
    (1003, 1, 'kim-minseo', 'minseo@example.com', '01010001003', '1992-11-23', TRUE, DATE_SUB(NOW(), INTERVAL 65 DAY), NOW(), NOW()),
    (1004, 1, 'choi-yuna', 'yuna@example.com', '01010001004', '1989-01-18', TRUE, DATE_SUB(NOW(), INTERVAL 38 DAY), NOW(), NOW()),
    (1005, 1, 'han-doyun', 'doyun@example.com', '01010001005', '1997-09-09', TRUE, DATE_SUB(NOW(), INTERVAL 22 DAY), NOW(), NOW()),
    (1006, 1, 'seo-arin', 'arin@example.com', '01010001006', '2000-05-05', TRUE, DATE_SUB(NOW(), INTERVAL 18 DAY), NOW(), NOW()),
    (1007, 1, 'jung-haeun', 'haeun@example.com', '01010001007', '1994-12-10', TRUE, DATE_SUB(NOW(), INTERVAL 14 DAY), NOW(), NOW()),
    (1008, 1, 'kang-woojin', 'woojin@example.com', '01010001008', '1991-04-21', TRUE, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), NOW());

INSERT INTO devices (
    device_id, project_id, user_id, external_id, platform, push_token,
    push_token_updated_at, token_valid, app_version, os_version, last_active_at, created_at, updated_at
) VALUES
    (2001, 1, 1001, 'device-park-junho', 'ANDROID', 'demo-token-1001', NOW(), TRUE, '1.4.0', 'Android 15', DATE_SUB(NOW(), INTERVAL 20 MINUTE), NOW(), NOW()),
    (2002, 1, 1002, 'device-lee-jieun', 'ANDROID', 'demo-token-1002', NOW(), TRUE, '1.4.0', 'Android 14', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW(), NOW()),
    (2003, 1, 1003, 'device-kim-minseo', 'IOS', 'demo-token-1003', NOW(), TRUE, '1.4.0', 'iOS 18', DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW(), NOW()),
    (2004, 1, 1004, 'device-choi-yuna', 'WEB', 'demo-token-1004', NOW(), TRUE, '1.4.0', 'Chrome 124', DATE_SUB(NOW(), INTERVAL 3 HOUR), NOW(), NOW()),
    (2005, 1, 1005, 'device-han-doyun', 'ANDROID', 'demo-token-1005', NOW(), TRUE, '1.4.0', 'Android 13', DATE_SUB(NOW(), INTERVAL 6 HOUR), NOW(), NOW()),
    (2006, 1, 1006, 'device-seo-arin', 'IOS', 'demo-token-1006', NOW(), TRUE, '1.4.0', 'iOS 17', DATE_SUB(NOW(), INTERVAL 8 HOUR), NOW(), NOW()),
    (2007, 1, 1007, 'device-jung-haeun', 'ANDROID', 'demo-token-1007', NOW(), TRUE, '1.4.0', 'Android 15', DATE_SUB(NOW(), INTERVAL 11 HOUR), NOW(), NOW()),
    (2008, 1, 1008, 'device-kang-woojin', 'WEB', 'demo-token-1008', NOW(), TRUE, '1.4.0', 'Edge 124', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW());

INSERT INTO user_tags (project_id, user_id, tag_key, tag_value, created_at, updated_at)
VALUES
    (1, 1001, 'interest', 'sneakers', NOW(), NOW()),
    (1, 1001, 'grade', 'VIP', NOW(), NOW()),
    (1, 1001, 'region', '서울', NOW(), NOW()),
    (1, 1002, 'interest', 'running', NOW(), NOW()),
    (1, 1002, 'grade', 'GOLD', NOW(), NOW()),
    (1, 1003, 'interest', 'sneakers', NOW(), NOW()),
    (1, 1004, 'region', '경기', NOW(), NOW()),
    (1, 1005, 'interest', 'loafers', NOW(), NOW()),
    (1, 1006, 'grade', 'NEW', NOW(), NOW()),
    (1, 1007, 'interest', 'sneakers', NOW(), NOW()),
    (1, 1008, 'region', '부산', NOW(), NOW());

INSERT INTO event_categories (category_id, project_id, name, type, description, created_at, updated_at)
VALUES
    (101, 1, 'app_open', 'DEFAULT', '앱 실행', NOW(), NOW()),
    (102, 1, 'search', 'CUSTOM', '상품 검색', NOW(), NOW()),
    (103, 1, 'product_view', 'CUSTOM', '상품 상세 조회', NOW(), NOW()),
    (104, 1, 'add_to_cart', 'CUSTOM', '장바구니 담기', NOW(), NOW()),
    (105, 1, 'purchase_complete', 'CUSTOM', '구매 완료', NOW(), NOW());

INSERT INTO event_schemas (schema_id, project_id, category_id, version, schema_def, effective_from, is_current, created_by, created_at)
VALUES
    (201, 1, 101, 1, JSON_ARRAY(JSON_OBJECT('key','source','type','STRING','required',false)), NOW(), TRUE, 1, NOW()),
    (202, 1, 102, 1, JSON_ARRAY(JSON_OBJECT('key','keyword','type','STRING','required',true), JSON_OBJECT('key','resultCount','type','NUMBER','required',false)), NOW(), TRUE, 1, NOW()),
    (203, 1, 103, 1, JSON_ARRAY(JSON_OBJECT('key','productId','type','STRING','required',true), JSON_OBJECT('key','category','type','STRING','required',true), JSON_OBJECT('key','price','type','NUMBER','required',false)), NOW(), TRUE, 1, NOW()),
    (204, 1, 104, 1, JSON_ARRAY(JSON_OBJECT('key','productId','type','STRING','required',true), JSON_OBJECT('key','price','type','NUMBER','required',false)), NOW(), TRUE, 1, NOW()),
    (205, 1, 105, 1, JSON_ARRAY(JSON_OBJECT('key','orderId','type','STRING','required',true), JSON_OBJECT('key','amount','type','NUMBER','required',true)), NOW(), TRUE, 1, NOW());

INSERT INTO event_schema_fields (schema_id, field_name, field_type, is_required, is_promoted, previous_names, created_at)
VALUES
    (201, 'source', 'STRING', FALSE, FALSE, NULL, NOW()),
    (202, 'keyword', 'STRING', TRUE, TRUE, NULL, NOW()),
    (202, 'resultCount', 'NUMBER', FALSE, FALSE, NULL, NOW()),
    (203, 'productId', 'STRING', TRUE, TRUE, NULL, NOW()),
    (203, 'category', 'STRING', TRUE, TRUE, NULL, NOW()),
    (203, 'price', 'NUMBER', FALSE, FALSE, NULL, NOW()),
    (204, 'productId', 'STRING', TRUE, TRUE, NULL, NOW()),
    (204, 'price', 'NUMBER', FALSE, FALSE, NULL, NOW()),
    (205, 'orderId', 'STRING', TRUE, TRUE, NULL, NOW()),
    (205, 'amount', 'NUMBER', TRUE, TRUE, NULL, NOW());

INSERT INTO event_daily_agg (log_date, project_id, category_id, user_id, event_count, first_at, last_at, updated_at)
VALUES
    (CURDATE(), 1, 101, 1001, 2, DATE_SUB(NOW(3), INTERVAL 6 HOUR), DATE_SUB(NOW(3), INTERVAL 20 MINUTE), NOW()),
    (CURDATE(), 1, 102, 1001, 3, DATE_SUB(NOW(3), INTERVAL 5 HOUR), DATE_SUB(NOW(3), INTERVAL 35 MINUTE), NOW()),
    (CURDATE(), 1, 103, 1001, 5, DATE_SUB(NOW(3), INTERVAL 4 HOUR), DATE_SUB(NOW(3), INTERVAL 24 MINUTE), NOW()),
    (CURDATE(), 1, 104, 1001, 1, DATE_SUB(NOW(3), INTERVAL 18 MINUTE), DATE_SUB(NOW(3), INTERVAL 18 MINUTE), NOW()),
    (CURDATE(), 1, 103, 1002, 4, DATE_SUB(NOW(3), INTERVAL 7 HOUR), DATE_SUB(NOW(3), INTERVAL 1 HOUR), NOW()),
    (CURDATE(), 1, 102, 1003, 2, DATE_SUB(NOW(3), INTERVAL 8 HOUR), DATE_SUB(NOW(3), INTERVAL 2 HOUR), NOW()),
    (DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1, 103, 1004, 6, DATE_SUB(NOW(3), INTERVAL 1 DAY), DATE_SUB(NOW(3), INTERVAL 1 DAY), NOW()),
    (DATE_SUB(CURDATE(), INTERVAL 2 DAY), 1, 105, 1005, 1, DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY), NOW());

INSERT INTO events (project_id, user_id, category_id, device_id, event_uuid, occurred_at, schema_version, event_data, created_at, updated_at)
VALUES
    (1, 1001, 101, 2001, UUID(), DATE_SUB(NOW(3), INTERVAL 6 HOUR), 1, JSON_OBJECT('source','home'), NOW(), NOW()),
    (1, 1001, 102, 2001, UUID(), DATE_SUB(NOW(3), INTERVAL 5 HOUR), 1, JSON_OBJECT('keyword','러닝화','resultCount',42), NOW(), NOW()),
    (1, 1001, 103, 2001, UUID(), DATE_SUB(NOW(3), INTERVAL 4 HOUR), 1, JSON_OBJECT('productId','shoe-neo-01','category','sneakers','price',129000), NOW(), NOW()),
    (1, 1001, 104, 2001, UUID(), DATE_SUB(NOW(3), INTERVAL 18 MINUTE), 1, JSON_OBJECT('productId','shoe-neo-01','price',129000), NOW(), NOW()),
    (1, 1002, 103, 2002, UUID(), DATE_SUB(NOW(3), INTERVAL 1 HOUR), 1, JSON_OBJECT('productId','run-lite-02','category','running','price',89000), NOW(), NOW()),
    (1, 1005, 105, 2005, UUID(), DATE_SUB(NOW(3), INTERVAL 2 DAY), 1, JSON_OBJECT('orderId','order-demo-001','amount',178000), NOW(), NOW());

INSERT INTO segments (
    segment_id, project_id, name, description, condition_tree, schema_version,
    estimated_count, estimated_at, last_built_at, build_status, created_by,
    is_deleted, created_at, updated_at
) VALUES
    (301, 1, '운동화 관심 이탈', '최근 7일 운동화 조회 후 미구매 고객',
     JSON_OBJECT('logic','and','conditions',JSON_ARRAY(
         JSON_OBJECT('type','event','categoryId',103,'filters',JSON_ARRAY(JSON_OBJECT('key','category','op','eq','value','sneakers')),'period',JSON_OBJECT('type','relative','days',7),'minCount',2),
         JSON_OBJECT('type','tag','key','interest','op','contains','value','sneakers')
     )), 4, 1820, NOW(), NOW(), 'READY', 1, FALSE, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW()),
    (302, 1, 'VIP 재구매 후보', 'VIP 등급과 최근 구매 이력 보유 고객',
     JSON_OBJECT('logic','and','conditions',JSON_ARRAY(
         JSON_OBJECT('type','tag','key','grade','op','eq','value','VIP'),
         JSON_OBJECT('type','event','categoryId',105,'filters',JSON_ARRAY(),'period',JSON_OBJECT('type','relative','days',30),'minCount',1)
     )), 4, 640, NOW(), NOW(), 'READY', 1, FALSE, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW()),
    (303, 1, '신규 온보딩', '가입 30일 이내 신규 고객',
     JSON_OBJECT('logic','and','conditions',JSON_ARRAY(
         JSON_OBJECT('type','user','key','signedUpDays','op','lte','value',30)
     )), 4, 3120, NOW(), NOW(), 'READY', 1, FALSE, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW());

INSERT INTO segment_members (segment_id, user_id, matched_at, expires_at, variables_json, variables_captured_at)
VALUES
    (301, 1001, NOW(3), NULL, JSON_OBJECT('externalId','park-junho','favoriteCategory','sneakers'), NOW(3)),
    (301, 1003, NOW(3), NULL, JSON_OBJECT('externalId','kim-minseo','favoriteCategory','sneakers'), NOW(3)),
    (301, 1007, NOW(3), NULL, JSON_OBJECT('externalId','jung-haeun','favoriteCategory','sneakers'), NOW(3)),
    (302, 1001, NOW(3), NULL, JSON_OBJECT('externalId','park-junho','grade','VIP'), NOW(3)),
    (303, 1006, NOW(3), NULL, JSON_OBJECT('externalId','seo-arin','grade','NEW'), NOW(3));

INSERT INTO segment_favorites (segment_id, member_id, created_at)
VALUES (301, 1, NOW()), (302, 1, NOW());

INSERT INTO message_templates (
    message_template_id, member_id, project_id, message_template_name,
    message_template_description, channel, message_template_title,
    message_template_body, message_template_image_url, required_variables,
    is_deleted, created_at, updated_at
) VALUES
    (401, 1, 1, '운동화 리타게팅 푸시', '운동화 조회 후 미구매 고객 대상', 'PUSH',
     '다시 보고 있던 운동화', '{{externalId}}님, 장바구니에 담긴 운동화가 곧 품절될 수 있어요.',
     NULL, JSON_ARRAY('externalId'), FALSE, NOW(), NOW()),
    (402, 1, 1, 'VIP 재구매 이메일', 'VIP 고객 재구매 유도', 'EMAIL',
     'VIP 전용 혜택이 도착했어요', '{{externalId}}님만을 위한 이번 주 혜택을 확인해보세요.',
     NULL, JSON_ARRAY('externalId'), FALSE, NOW(), NOW()),
    (403, 1, 1, '신규 고객 온보딩', '첫 구매 전환 유도', 'PUSH',
     '첫 구매 혜택 안내', '{{externalId}}님, 첫 구매 쿠폰이 준비되어 있어요.',
     NULL, JSON_ARRAY('externalId'), FALSE, NOW(), NOW());

INSERT INTO campaigns (
    campaign_id, project_id, message_template_id, segment_id, member_id,
    campaign_name, campaign_description, campaign_type, channel, campaign_status,
    send_mode, recurring_cron, repeat_type, repeat_day_of_week, repeat_day_of_month,
    repeat_time, recurring_count, recurring_execution_count, next_run_at,
    version, is_deleted, created_at, updated_at
) VALUES
    (501, 1, 401, 301, 1, '운동화 관심 이탈 리마인드', '조회 후 구매하지 않은 고객에게 푸시 발송', 'ONE_TIME', 'PUSH', 'ACTIVE', 'IMMEDIATE', NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, 0, FALSE, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW()),
    (502, 1, 402, 302, 1, 'VIP 주말 재구매 캠페인', 'VIP 고객 이메일 혜택 발송', 'ONE_TIME', 'EMAIL', 'ENDED', 'SCHEDULED', NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, 0, FALSE, DATE_SUB(NOW(), INTERVAL 8 DAY), NOW()),
    (503, 1, 403, 303, 1, '신규 고객 첫 구매 전환', '신규 가입 고객 첫 구매 유도', 'ONE_TIME', 'PUSH', 'ENDED', 'IMMEDIATE', NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, 0, FALSE, DATE_SUB(NOW(), INTERVAL 12 DAY), NOW()),
    (504, 1, 401, 301, 1, '장바구니 이탈 3시간 후속', '장바구니 담기 후 미구매자 후속 알림', 'AUTOMATION', 'KAKAO', 'DRAFT', 'RECURRING', NULL, 'DAILY', NULL, NULL, '10:00:00', 7, 0, DATE_ADD(NOW(), INTERVAL 1 DAY), 0, FALSE, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
    (505, 1, 402, NULL, 1, '전체 고객 브랜드 세일', '전체 고객 대상 시즌 세일 안내', 'ONE_TIME', 'SMS', 'PAUSED', 'SCHEDULED', NULL, NULL, NULL, NULL, NULL, NULL, 0, DATE_ADD(NOW(), INTERVAL 2 DAY), 0, FALSE, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW());

INSERT INTO campaign_executions (
    campaign_execution_id, campaign_id, run_round, campaign_execution_status,
    send_started_at, send_completed_at, target_count, is_deleted, created_at, updated_at,
    version
) VALUES
    (601, 501, 1, 'SUCCESS', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY) + INTERVAL 10 MINUTE, 1820, FALSE, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
    (602, 502, 1, 'SUCCESS', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 12 MINUTE, 640, FALSE, DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), 0),
    (603, 503, 1, 'SUCCESS', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY) + INTERVAL 9 MINUTE, 3120, FALSE, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), 0);

INSERT INTO campaign_stats (campaign_id, created_at, updated_at, clicked_count, delivered_count, failed_count, sent_count, total_target)
VALUES
    (501, NOW(), NOW(), 286, 1710, 27, 1793, 1820),
    (502, NOW(), NOW(), 118, 604, 18, 622, 640),
    (503, NOW(), NOW(), 390, 2980, 45, 3075, 3120),
    (504, NOW(), NOW(), 0, 0, 0, 0, 1820),
    (505, NOW(), NOW(), 82, 1300, 30, 1380, 4200);

INSERT INTO campaign_stats_daily (
    project_id, campaign_id, channel, bucket_date, sent_count, delivered_count, failed_count,
    open_count, click_count, convert_count, delivery_rate, open_rate, click_rate, convert_rate, updated_at
) VALUES
    (1, 501, 'PUSH', DATE_SUB(CURDATE(), INTERVAL 6 DAY), 260, 246, 6, 132, 46, 13, 94.62, 53.66, 34.85, 5.00, NOW()),
    (1, 501, 'PUSH', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 320, 304, 5, 168, 58, 18, 95.00, 55.26, 34.52, 5.63, NOW()),
    (1, 501, 'PUSH', DATE_SUB(CURDATE(), INTERVAL 4 DAY), 410, 391, 8, 226, 74, 27, 95.37, 57.80, 32.74, 6.59, NOW()),
    (1, 501, 'PUSH', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 290, 274, 4, 151, 51, 16, 94.48, 55.11, 33.77, 5.52, NOW()),
    (1, 501, 'PUSH', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 290, 274, 4, 148, 48, 14, 94.48, 54.01, 32.43, 4.83, NOW()),
    (1, 501, 'PUSH', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 223, 221, 0, 122, 37, 10, 99.10, 55.20, 30.33, 4.48, NOW()),
    (1, 502, 'EMAIL', DATE_SUB(CURDATE(), INTERVAL 6 DAY), 220, 209, 6, 142, 51, 19, 95.00, 67.94, 35.92, 8.64, NOW()),
    (1, 502, 'EMAIL', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 210, 202, 5, 137, 44, 17, 96.19, 67.82, 32.12, 8.10, NOW()),
    (1, 502, 'EMAIL', DATE_SUB(CURDATE(), INTERVAL 4 DAY), 192, 193, 7, 121, 23, 9, 100.00, 62.69, 19.01, 4.69, NOW()),
    (1, 503, 'PUSH', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 1050, 1010, 16, 540, 181, 64, 96.19, 53.47, 33.52, 6.10, NOW()),
    (1, 503, 'PUSH', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 980, 951, 15, 498, 162, 53, 97.04, 52.37, 32.53, 5.41, NOW()),
    (1, 503, 'PUSH', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1045, 1019, 14, 522, 170, 57, 97.51, 51.23, 32.57, 5.45, NOW()),
    (1, 505, 'SMS', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1380, 1300, 30, 0, 82, 20, 94.20, NULL, NULL, 1.45, NOW());

INSERT INTO project_stats_daily (
    project_id, bucket_date, active_user_count, new_user_count, churn_user_count,
    total_sent, total_delivered, total_failed, total_open, total_click, total_convert,
    push_open_rate, overall_click_rate, overall_convert_rate, active_campaign_count, updated_at
) VALUES
    (1, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 1840, 92, 14, 480, 455, 12, 274, 97, 32, 60.22, 35.40, 6.67, 2, NOW()),
    (1, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 1920, 104, 13, 530, 506, 10, 305, 102, 35, 60.28, 33.44, 6.60, 2, NOW()),
    (1, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 2105, 118, 12, 602, 584, 15, 347, 97, 36, 59.42, 27.95, 5.98, 2, NOW()),
    (1, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 2380, 126, 11, 1340, 1284, 20, 691, 232, 80, 53.82, 33.57, 5.97, 2, NOW()),
    (1, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 2540, 143, 9, 1270, 1225, 19, 646, 210, 67, 52.73, 32.51, 5.28, 2, NOW()),
    (1, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 2780, 151, 8, 2648, 2540, 44, 644, 289, 87, 25.35, 44.88, 3.29, 3, NOW());

INSERT INTO audit_logs (project_id, member_id, action, target_type, target_id, detail, ip_address, user_agent, created_at)
VALUES
    (1, 1, 'LOGIN', 'Member', 1, JSON_OBJECT('email','demo@butterfly.io','role','OWNER'), '127.0.0.1', 'StoryBoard', DATE_SUB(NOW(), INTERVAL 2 HOUR)),
    (1, 1, 'SEGMENT_CREATE', 'Segment', 301, JSON_OBJECT('name','운동화 관심 이탈'), '127.0.0.1', 'StoryBoard', DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
    (1, 1, 'CAMPAIGN_ACTIVATE', 'Campaign', 501, JSON_OBJECT('name','운동화 관심 이탈 리마인드'), '127.0.0.1', 'StoryBoard', DATE_SUB(NOW(), INTERVAL 70 MINUTE));

SET FOREIGN_KEY_CHECKS = 1;
