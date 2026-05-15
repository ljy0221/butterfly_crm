-- Loadtest seed 2/3 — Segment 1건 + segment_members 200건
--
-- 핵심: SegmentMemberLookupAdapter 가 segment_id NULL 시 emptyList 반환 →
--      반드시 segment_id 가 연결되어야 발송 대상이 잡힘.
--      segment_members.expires_at IS NULL → 무기한 활성 (findActiveBySegmentId 의 OR 조건).
--
-- 멱등성: 같은 (project_id, name) UNIQUE 키라 DELETE 후 INSERT.
--
-- placeholder:
--   ${PROJECT_ID}, ${MEMBER_ID}

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 기존 loadtest segment 정리 (segment_members 는 segment_id FK 기준 CASCADE 없음 → 명시 DELETE)
DELETE FROM segment_members
 WHERE segment_id IN (
   SELECT segment_id FROM segments
    WHERE project_id = ${PROJECT_ID}
      AND name = 'LoadTest-Segment'
 );

DELETE FROM segments
 WHERE project_id = ${PROJECT_ID}
   AND name = 'LoadTest-Segment';

-- 2. segment 1건 — condition_tree 는 match-all 형태 (실제 build 안 함, 직접 segment_members INSERT)
INSERT INTO segments (project_id, name, description, condition_tree, schema_version,
                      estimated_count, build_status, created_by, is_deleted,
                      created_at, updated_at)
VALUES (${PROJECT_ID},
        'LoadTest-Segment',
        '200건 부하 테스트 전용 세그먼트 (S14P31B101-299)',
        JSON_OBJECT('logic', 'and', 'conditions', JSON_ARRAY()),
        4,
        200,
        'READY',
        ${MEMBER_ID},
        FALSE,
        NOW(),
        NOW());

SET @segment_id := LAST_INSERT_ID();

-- 3. segment_members 200건 — loadtest user 와 1:1
INSERT INTO segment_members (segment_id, user_id, matched_at, expires_at)
SELECT
    @segment_id,
    u.user_id,
    NOW(3),
    NULL
FROM users u
WHERE u.project_id = ${PROJECT_ID}
  AND u.external_id LIKE 'loadtest-user-%';

SET FOREIGN_KEY_CHECKS = 1;

SELECT @segment_id AS segment_id,
       (SELECT COUNT(*) FROM segment_members WHERE segment_id = @segment_id) AS member_count;
