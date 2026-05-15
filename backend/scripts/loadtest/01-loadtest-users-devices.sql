-- Loadtest seed 1/3 — User 200건 + Device 200건
--
-- 멱등성 보장: external_id LIKE 'loadtest-%' 범위만 DELETE 후 INSERT.
-- 운영 데이터(prefix 다름) 영향 없음.
--
-- placeholder:
--   ${PROJECT_ID}  → seed.sh 가 LOADTEST_PROJECT_ID 환경변수로 치환
--
-- 실 디바이스 토큰 2건 (에뮬레이터 5556, 5558) 은 마지막에 token_valid=TRUE 로 INSERT.
-- 나머지 198건은 'loadtest-fake-token-NNNN' (FCM 응답 UNREGISTERED 예상).

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 기존 loadtest user/device 정리
DELETE FROM devices
 WHERE project_id = ${PROJECT_ID}
   AND external_id LIKE 'loadtest-dev-%';

DELETE FROM users
 WHERE project_id = ${PROJECT_ID}
   AND external_id LIKE 'loadtest-user-%';

-- 2. user 200건 — recursive numbers 테이블 만든 뒤 cross-join
--    MySQL 8.0 의 WITH RECURSIVE 로 1..200 시퀀스 생성
INSERT INTO users (project_id, external_id, push_opt_in, signed_up_at, created_at, updated_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 200
)
SELECT
    ${PROJECT_ID},
    CONCAT('loadtest-user-', LPAD(n, 4, '0')),
    TRUE,
    NOW() - INTERVAL (n MOD 30) DAY,
    NOW(),
    NOW()
FROM seq;

-- 3. device 200건 — 방금 만든 user 와 1:1 매칭
--    push_token 198건은 가짜, 마지막 2건은 실 에뮬레이터 토큰으로 UPDATE
INSERT INTO devices (project_id, user_id, external_id, platform,
                     push_token, push_token_updated_at,
                     token_valid, app_version, os_version, last_active_at, created_at)
SELECT
    u.project_id,
    u.user_id,
    CONCAT('loadtest-dev-', SUBSTRING(u.external_id, 15)),  -- 'loadtest-user-0001' → '0001'
    'ANDROID',
    CONCAT('loadtest-fake-token-', SUBSTRING(u.external_id, 15)),
    NOW(),
    TRUE,
    '1.0.0',
    '14',
    NOW(),
    NOW()
FROM users u
WHERE u.project_id = ${PROJECT_ID}
  AND u.external_id LIKE 'loadtest-user-%';

-- 4. 실 에뮬레이터 토큰 2건 덮어쓰기 (loadtest-user-0001 / 0002)
UPDATE devices
   SET push_token = 'c_cffeHBSdydSqRu5oQoDN:APA91bHUbRqPzZCcRVxSEOGT_SfmtgHv8fKAm9bsYtlnobqMzwEDMkXtfKP0jEqdntTSpFFKIHLNI5pSFW1ptAdZKEbTJyarJ66Yoz5lFOeDGN5bnVGfJ74',
       push_token_updated_at = NOW(),
       last_active_at = NOW()
 WHERE project_id = ${PROJECT_ID}
   AND external_id = 'loadtest-dev-0001';

UPDATE devices
   SET push_token = 'e2iAC-kaRCGHUr2iNNQeL6:APA91bFGORfKh7fkG8WUplcrmg7rb34YKhvT4dg_IPpw_YnbGjGCNWEqltSxLOZX7swAhqz0PNTYo7DLlskkclY78ZhjWfe73fsjpSECwzeAr2rem2-0qw4',
       push_token_updated_at = NOW(),
       last_active_at = NOW()
 WHERE project_id = ${PROJECT_ID}
   AND external_id = 'loadtest-dev-0002';

SET FOREIGN_KEY_CHECKS = 1;

SELECT
    (SELECT COUNT(*) FROM users   WHERE project_id = ${PROJECT_ID} AND external_id LIKE 'loadtest-user-%') AS users_seeded,
    (SELECT COUNT(*) FROM devices WHERE project_id = ${PROJECT_ID} AND external_id LIKE 'loadtest-dev-%') AS devices_seeded;
