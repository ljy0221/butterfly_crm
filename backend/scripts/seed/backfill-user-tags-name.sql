-- ============================================================================
-- backfill-user-tags-name.sql (S14P31B101-308)
--
-- 한 프로젝트 내 user_tags 에 'name' tag 가 없는 모든 user 에게 기본값을 채운다.
--   tag_value 기본값 = users.external_id  (User 엔티티에 name 컬럼이 없는 현 도메인의
--   합리적 대체값 — loadtest-user-00001 같이 식별 가능한 문자열이 적어도 보임).
--
-- 멱등성: WHERE NOT EXISTS 로 이미 있는 row 건드리지 않음. 운영자가 SDK/관리 API 로
-- 실제 이름을 박은 user 는 그대로 보존.
--
-- 사용 (staging MySQL k14b101 클러스터, 실 db 비번은 환경에서 주입):
--   mysql -h <host> -P <port> -u butterfly -p butterfly \
--         -e "SET @pid := <PROJECT_ID>; SOURCE backfill-user-tags-name.sql;"
--
-- 또는 K3s 마스터에서:
--   kubectl exec -n butterfly deploy/mysql -- \
--     mysql -uroot -p<pw> butterfly < scripts/seed/backfill-user-tags-name.sql
--
-- ⚠ PROJECT_ID 는 호출자가 @pid 세션변수로 미리 지정. 미지정이면 NULL → 0 row 영향.
-- ============================================================================

INSERT INTO user_tags (project_id, user_id, tag_key, tag_value, created_at, updated_at)
SELECT
    u.project_id,
    u.user_id,
    'name'        AS tag_key,
    u.external_id AS tag_value,   -- User 엔티티에 name 컬럼이 없어 external_id 를 대체값으로
    NOW(),
    NOW()
  FROM users u
 WHERE u.project_id = @pid
   AND NOT EXISTS (
       SELECT 1
         FROM user_tags ut
        WHERE ut.project_id = u.project_id
          AND ut.user_id    = u.user_id
          AND ut.tag_key    = 'name'
   );

SELECT
    @pid                                                            AS project_id,
    (SELECT COUNT(*) FROM users      WHERE project_id = @pid)       AS users_total,
    (SELECT COUNT(*) FROM user_tags  WHERE project_id = @pid
                                       AND tag_key    = 'name')     AS users_with_name_tag;
