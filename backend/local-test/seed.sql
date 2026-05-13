USE butterfly;

INSERT INTO members
  (project_id, email, password_hash, name, phone, birth_date, role_type, created_at, updated_at)
VALUES
  (1, 'owner@test.local', 'noop', 'tester', '01000000000', '2000-01-01', 'OWNER', NOW(), NOW());

INSERT INTO projects
  (project_id, name, business_no, company_auth_no, owner_id, public_api_key, plan, created_at, updated_at)
VALUES
  (1, 'Test Project', 'BN-1', 'AUTH-1', 1, 'pak_test_1', 'PRO', NOW(), NOW());

INSERT INTO message_templates (
  member_id, project_id, channel,
  message_template_name, message_template_description,
  message_template_title, message_template_body, message_template_image_url,
  required_variables, created_at, updated_at
) VALUES (
  1, 1, 'PUSH',
  'CampaignFlow Template', 'E2E test template',
  'Hello from campaign flow', 'Sent via worker pipeline', NULL,
  JSON_ARRAY(), NOW(), NOW()
);

INSERT INTO segments (
  project_id, name, description, condition_tree, schema_version,
  estimated_count, build_status, created_by, created_at, updated_at
) VALUES (
  1, 'AllUsers', 'match all',
  JSON_OBJECT('logic', 'and', 'conditions', JSON_ARRAY()),
  4, 0, 'READY', 1, NOW(), NOW()
);
