-- Loadtest seed 3/3 — MessageTemplate 1건 + Campaign 1건 (DRAFT)
--
-- send_mode=IMMEDIATE + campaign_status=DRAFT 로 INSERT.
-- trigger-campaign.ps1 가 PATCH /api/v1/projects/{pid}/campaigns/{cid}/status 로
-- DRAFT → ACTIVE 전환 시점에 CampaignActivator 가 campaign.send.requested 토픽으로 발행.
--
-- placeholder:
--   ${PROJECT_ID}, ${MEMBER_ID}

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 기존 loadtest 캠페인/템플릿 정리 (멱등)
DELETE FROM campaign_executions
 WHERE campaign_id IN (
   SELECT campaign_id FROM campaigns
    WHERE project_id = ${PROJECT_ID}
      AND campaign_name = 'LoadTest-Campaign-200'
 );

DELETE FROM campaigns
 WHERE project_id = ${PROJECT_ID}
   AND campaign_name = 'LoadTest-Campaign-200';

DELETE FROM message_templates
 WHERE project_id = ${PROJECT_ID}
   AND message_template_name = 'LoadTest-Template';

-- 2. 메시지 템플릿 1건 (PUSH)
INSERT INTO message_templates (member_id, project_id,
                               message_template_name, message_template_description,
                               channel,
                               message_template_title, message_template_body,
                               required_variables, is_deleted, created_at, updated_at)
VALUES (${MEMBER_ID}, ${PROJECT_ID},
        'LoadTest-Template', '200건 부하 테스트 전용 (S14P31B101-299)',
        'PUSH',
        'LoadTest Push',
        'Butterfly 부하 테스트 메시지입니다.',
        JSON_ARRAY(), FALSE, NOW(), NOW());

SET @template_id := LAST_INSERT_ID();

-- 3. 부하 테스트 segment_id 조회
SET @segment_id := (
    SELECT segment_id FROM segments
     WHERE project_id = ${PROJECT_ID}
       AND name = 'LoadTest-Segment'
     LIMIT 1
);

-- 4. 캠페인 1건 (DRAFT)
INSERT INTO campaigns (project_id, message_template_id, segment_id, member_id,
                       campaign_name, campaign_description,
                       campaign_type, channel, campaign_status, send_mode,
                       version, is_deleted, created_at, updated_at)
VALUES (${PROJECT_ID}, @template_id, @segment_id, ${MEMBER_ID},
        'LoadTest-Campaign-200', '200건 부하 테스트 (S14P31B101-299)',
        'ONE_TIME', 'PUSH', 'DRAFT', 'IMMEDIATE',
        0, FALSE, NOW(), NOW());

SET @campaign_id := LAST_INSERT_ID();

SET FOREIGN_KEY_CHECKS = 1;

SELECT @template_id AS template_id,
       @segment_id  AS segment_id,
       @campaign_id AS campaign_id;
