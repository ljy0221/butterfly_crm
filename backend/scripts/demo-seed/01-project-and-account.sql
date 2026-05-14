-- 5/22 시연용 — 프로젝트/계정/메시지 템플릿 시드
-- 멱등성: project_id=1 범위 데이터를 먼저 정리 후 재적재
--
-- [주의] members.password_hash 의 $2a$10$REPLACE_BCRYPT_HASH_FOR_demo1234 는 반드시 실제 bcrypt 해시로
-- 교체해야 합니다. 생성 방법:
--   htpasswd -bnBC 10 "" demo1234 | tr -d ':\n'
-- 출력된 해시를 아래 VALUES 에 붙여 넣으세요.
--
-- [주의] message_templates 컬럼명: V14 마이그레이션으로 plural 컬럼(message_templates_*) 이
-- 제거됐고, singular(message_template_*) 만 남아 있습니다.

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM message_templates    WHERE project_id = 1;
DELETE FROM members              WHERE project_id = 1;
DELETE FROM projects             WHERE project_id = 1;

INSERT INTO projects (project_id, name, business_no, company_auth_no, owner_id,
                      public_api_key, daily_limit, plan, is_deleted, created_at, updated_at)
VALUES (1, '데모 이커머스', '000-00-00000', 'demo-auth-no', 1,
        'pk_live_demoxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 100000, 'PRO', FALSE, NOW(), NOW());

-- 운영자 — OWNER role
-- member_status: V13 추가 컬럼 (NOT NULL DEFAULT 'ACTIVE')
INSERT INTO members (member_id, project_id, email, password_hash, name, phone, birth_date,
                     role_type, member_status, is_deleted, created_at, updated_at)
VALUES (1, 1, 'demo@butterfly.io',
        '$2a$10$REPLACE_BCRYPT_HASH_FOR_demo1234',
        '데모운영자', '01000000000', '1990-01-01',
        'OWNER', 'ACTIVE', FALSE, NOW(), NOW());

-- 메시지 템플릿 — 운동화 리타게팅
-- 컬럼명은 singular (V14 이후): message_template_name, message_template_title 등
INSERT INTO message_templates (message_template_id, member_id, project_id,
                               message_template_name, message_template_description,
                               channel,
                               message_template_title, message_template_body,
                               required_variables, is_deleted, created_at, updated_at)
VALUES (1, 1, 1,
        '운동화 리타게팅 v1', '운동화 카테고리 조회 미구매자 푸시',
        'PUSH',
        '운동화 보러 오셨던 분께',
        '{{externalId}}님, 보고 계신 운동화 어떠세요? 지금 확인하세요!',
        JSON_ARRAY('externalId'),
        FALSE, NOW(), NOW());

SET FOREIGN_KEY_CHECKS = 1;
