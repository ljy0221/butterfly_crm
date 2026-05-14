-- 5/22 시연용 — 페르소나 2명 + 노이즈 10명 + 이벤트 시드
--
-- 페르소나:
--   이지은 (user_id=73)  : 7일내 운동화 product_view 3회 이상 → 세그먼트 충족
--   박준호 (user_id=142) : 7일내 운동화 product_view 2회 (시연 중 +1회로 진입 유도)
--
-- devices.external_id: V7 마이그레이션으로 추가된 NOT NULL DEFAULT '' 컬럼
--   → INSERT 시 명시적으로 값을 채워야 UNIQUE KEY (project_id, external_id) 위반 없음
--
-- 노이즈 200명은 cross-join 방식으로 10건 적재 (시연 임팩트상 충분)

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM events           WHERE project_id = 1;
DELETE FROM event_categories WHERE project_id = 1;
DELETE FROM devices          WHERE project_id = 1;
DELETE FROM users            WHERE project_id = 1;

-- ──────────────────────────────────────────────────────────────────────────────
-- 이벤트 카테고리 4개
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO event_categories (category_id, project_id, name, type, description, created_at)
VALUES
(1, 1, 'product_view', 'CUSTOM',  '상품 상세 페이지 조회', NOW()),
(2, 1, 'page_view',    'DEFAULT', '일반 페이지 조회',      NOW()),
(3, 1, 'add_to_cart',  'CUSTOM',  '장바구니 담기',          NOW()),
(4, 1, 'purchase',     'CUSTOM',  '구매 완료',              NOW());

-- ──────────────────────────────────────────────────────────────────────────────
-- 이지은 (페르소나 A) — user_id=73
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO users (user_id, project_id, external_id, push_opt_in, signed_up_at, created_at, updated_at)
VALUES (73, 1, 'user-73', TRUE, '2026-04-22 10:30:00', NOW(), NOW());

INSERT INTO devices (device_id, project_id, user_id, external_id, platform,
                     push_token, push_token_updated_at,
                     token_valid, app_version, os_version, last_active_at, created_at)
VALUES (730, 1, 73, 'dev-user-73-ios', 'IOS',
        'test-ios-token-user-73', NOW(),
        TRUE, '2.1.0', '17.0', NOW(), NOW());

-- ──────────────────────────────────────────────────────────────────────────────
-- 박준호 (페르소나 B) — user_id=142
-- 시연 1시간 전 Android 실기기로 device + push_token 등록 예정
-- 여기서는 user 시드만 적재; device 는 앱 첫 실행 시 자동 등록됨
-- (device FK NOT NULL 이므로 이벤트 적재 시 device_id=730 임시 사용)
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO users (user_id, project_id, external_id, push_opt_in, signed_up_at, created_at, updated_at)
VALUES (142, 1, 'user-142', TRUE, '2026-04-25 14:20:00', NOW(), NOW());

-- ──────────────────────────────────────────────────────────────────────────────
-- 노이즈 사용자 10명 (user_id 200..209)
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO users (user_id, project_id, external_id, push_opt_in, signed_up_at, created_at, updated_at)
SELECT 199 + n, 1, CONCAT('user-noise-', n),
       (n % 4) != 0,
       DATE_SUB(NOW(), INTERVAL (n MOD 90) DAY),
       NOW(), NOW()
FROM (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) seq;

-- ──────────────────────────────────────────────────────────────────────────────
-- 이지은 이벤트 — 운동화 product_view 10건
-- 최근 7일 내 3건 (Day-5, Day-3, Day-1) → 세그먼트 조건 충족
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO events (project_id, user_id, category_id, device_id, event_uuid,
                    occurred_at, schema_version, event_data, created_at)
VALUES
(1, 73, 1, 730, 'demoevt-73-01', NOW() - INTERVAL 28 DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1001','productName','러닝화 A','price',75000), NOW()),
(1, 73, 1, 730, 'demoevt-73-02', NOW() - INTERVAL 24 DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1002','productName','러닝화 B','price',89000), NOW()),
(1, 73, 1, 730, 'demoevt-73-03', NOW() - INTERVAL 19 DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1003','productName','러닝화 C','price',95000), NOW()),
(1, 73, 1, 730, 'demoevt-73-04', NOW() - INTERVAL 14 DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1004','productName','러닝화 D','price',110000), NOW()),
(1, 73, 1, 730, 'demoevt-73-05', NOW() - INTERVAL 10 DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1005','productName','러닝화 E','price',125000), NOW()),
(1, 73, 1, 730, 'demoevt-73-06', NOW() - INTERVAL 9  DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1006','productName','러닝화 F','price',89000), NOW()),
(1, 73, 1, 730, 'demoevt-73-07', NOW() - INTERVAL 8  DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1007','productName','러닝화 G','price',99000), NOW()),
(1, 73, 1, 730, 'demoevt-73-08', NOW() - INTERVAL 5  DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1008','productName','러닝화 H','price',65000), NOW()),
(1, 73, 1, 730, 'demoevt-73-09', NOW() - INTERVAL 3  DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1009','productName','러닝화 I','price',78000), NOW()),
(1, 73, 1, 730, 'demoevt-73-10', NOW() - INTERVAL 1  DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1010','productName','러닝화 J','price',109000), NOW());

-- ──────────────────────────────────────────────────────────────────────────────
-- 박준호 이벤트 — 운동화 product_view 7건
-- 최근 7일 내 2건 (Day-4, Day-2) → 시연 중 +1회로 3회 진입
-- device FK NOT NULL 이므로 device_id=730 (이지은 디바이스) 임시 사용
-- 실기기 등록 후에는 새 이벤트가 실제 device_id 로 들어옴
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO events (project_id, user_id, category_id, device_id, event_uuid,
                    occurred_at, schema_version, event_data, created_at)
VALUES
(1, 142, 1, 730, 'demoevt-142-01', NOW() - INTERVAL 25 DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1101'), NOW()),
(1, 142, 1, 730, 'demoevt-142-02', NOW() - INTERVAL 20 DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1102'), NOW()),
(1, 142, 1, 730, 'demoevt-142-03', NOW() - INTERVAL 17 DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1103'), NOW()),
(1, 142, 1, 730, 'demoevt-142-04', NOW() - INTERVAL 12 DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1104'), NOW()),
(1, 142, 1, 730, 'demoevt-142-05', NOW() - INTERVAL 9  DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1105'), NOW()),
(1, 142, 1, 730, 'demoevt-142-06', NOW() - INTERVAL 4  DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1106'), NOW()),
(1, 142, 1, 730, 'demoevt-142-07', NOW() - INTERVAL 2  DAY, 1,
 JSON_OBJECT('category','운동화','productId','sku-1107'), NOW());

SET FOREIGN_KEY_CHECKS = 1;
