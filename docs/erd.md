CREATE TABLE `project_members` (
`project_id`	BIGINT	NOT NULL	COMMENT '소속 프로젝트 ID',
`member_id`	BIGINT	NOT NULL	COMMENT '소속 회원 ID',
`role`	ENUM('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')	NOT NULL	COMMENT '권한 역할',
`created_at`	DATETIME	NOT NULL	COMMENT '참여일시'
);

CREATE TABLE `notification_logs` (
`log_id`	BIGINT	NOT NULL	COMMENT '로그 고유 ID',
`project_id`	BIGINT	NOT NULL	COMMENT '소속 프로젝트 ID',
`campaign_id`	BIGINT	NOT NULL	COMMENT '발송된 캠페인 ID',
`user_id`	BIGINT	NULL	COMMENT '수신 유저 ID',
`device_id`	BIGINT	NOT NULL	COMMENT '수신 기기 ID',
`status`	ENUM('SENT', 'DELIVERED', 'CLICKED', 'FAILED')	NOT NULL	COMMENT '발송 상태',
`error_code`	VARCHAR(50)	NULL	COMMENT '에러 코드 (실패 시)',
`error_message`	VARCHAR(500)	NULL	COMMENT '에러 메시지 (실패 시)',
`sent_at`	DATETIME	NULL	COMMENT '발송 시각',
`delivered_at`	DATETIME	NULL	COMMENT '도달 시각',
`clicked_at`	DATETIME	NULL	COMMENT '클릭 시각',
`created_at`	DATETIME	NOT NULL	COMMENT '로그 생성 시각'
);

CREATE TABLE `users` (
`user_id`	BIGINT	NOT NULL	COMMENT '유저 고유 ID (내부)',
`project_id`	BIGINT	NOT NULL	COMMENT '프로젝트 고유 ID (tenant_id)',
`email`	VARCHAR(255)	NOT NULL	COMMENT '이메일',
`push_opt_in`	BOOLEAN	NOT NULL	COMMENT '푸시 수신 동의 여부',
`created_at`	DATETIME	NOT NULL	COMMENT '생성일시',
`updated_at`	DATETIME	NOT NULL	COMMENT '수정일시'
);

CREATE TABLE `campaign_stats` (
`campaign_id`	BIGINT	NOT NULL	COMMENT '캠페인 ID (1:1 매핑)',
`total_target`	INT	NOT NULL	COMMENT '전체 발송 대상 수',
`sent_count`	INT	NOT NULL	COMMENT 'FCM 발송 성공 수',
`delivered_count`	INT	NOT NULL	COMMENT '기기 도달 확인 수',
`clicked_count`	INT	NOT NULL	COMMENT '알림 클릭 수',
`failed_count`	INT	NOT NULL	COMMENT '발송 실패 수',
`updated_at`	DATETIME	NOT NULL	COMMENT '마지막 갱신 시각'
);

CREATE TABLE `events_category` (
`events_category_id`	BIGINT	NOT NULL	COMMENT '태그 정의 고유 ID',
`tag_key`	VARCHAR(100)	NOT NULL	COMMENT '태그 키 (예: region, grade)',
`display_name`	VARCHAR(100)	NOT NULL	COMMENT '대시보드 표시명 (예: 거주지역)',
`tag_type`	ENUM('STRING', 'NUMBER', 'BOOLEAN')	NOT NULL	DEFAULT 'STRING'	COMMENT '태그 값 타입',
`created_at`	DATETIME	NOT NULL	COMMENT '생성일시'
);

CREATE TABLE `projects` (
`project_id`	BIGINT	NOT NULL	COMMENT '프로젝트 고유 ID (tenant_id)',
`name`	VARCHAR(255)	NOT NULL	COMMENT '프로젝트 이름 (예: 쇼핑몰 앱)',
`public_api_key`	VARCHAR(100)	NOT NULL	COMMENT 'SDK용 공개 키 (pk_live_xxx)',
`secret_api_key`	VARCHAR(100)	NOT NULL	COMMENT '서버 API용 비밀 키 (sk_live_xxx)',
`fcm_credentials`	TEXT	NULL	COMMENT 'Firebase 서비스 계정 JSON (AES 암호화)',
`limit`	TINYINT	NULL	COMMENT '일일 발송 한도(유저 피로도 측면)',
`created_at`	DATETIME	NOT NULL	COMMENT '생성일시',
`updated_at`	DATETIME	NOT NULL	COMMENT '수정일시'
);

CREATE TABLE `segment_rules` (
`rule_id`	BIGINT	NOT NULL	COMMENT '규칙 고유 ID',
`segment_id`	BIGINT	NOT NULL	COMMENT '소속 세그먼트 ID',
`rule_type`	ENUM('TAG', 'EVENT', 'DEVICE', 'USER')	NOT NULL	COMMENT '규칙 타입 (JOIN 대상 결정)',
`field`	VARCHAR(100)	NOT NULL	COMMENT '필드명 (예: region, platform)',
`operator`	ENUM('EQ', 'NEQ', 'GT', 'LT', 'GTE', 'LTE', 'IN', 'CONTAINS')	NOT NULL	COMMENT '연산자',
`value`	VARCHAR(500)	NOT NULL	COMMENT '비교 값 (예: 서울, VIP,VVIP)',
`created_at`	DATETIME	NOT NULL	COMMENT '생성일시'
);

CREATE TABLE `devices` (
`device_id`	BIGINT	NOT NULL	COMMENT '기기 ID',
`user_id`	BIGINT	NULL	COMMENT '유저 ID',
`platform`	ENUM('ANDROID', 'IOS', 'WEB')	NOT NULL	COMMENT '플랫폼명',
`push_token`	VARCHAR(500)	NOT NULL	COMMENT 'FCM 토큰',
`push_token_updated_at`	DATETIME	NOT NULL	COMMENT '토큰 수정일시',
`token_valid`	BOOLEAN	NOT NULL	COMMENT '토큰 유효 여부 (4xx시 FALSE)',
`timezone`	VARCHAR(50)	NULL	COMMENT '기기 타임존',
`language`	VARCHAR(10)	NULL	COMMENT '기기 언어 설정',
`app_version`	VARCHAR(50)	NULL	COMMENT '앱 버전',
`os_version`	VARCHAR(50)	NULL	COMMENT 'OS 버전',
`last_active_at`	DATETIME	NULL	COMMENT '마지막 접속 시간',
`created_at`	DATETIME	NOT NULL	COMMENT '등록일시'
);

CREATE TABLE `segments` (
`segment_id`	BIGINT	NOT NULL	COMMENT '세그먼트 고유 ID',
`project_id`	BIGINT	NOT NULL	COMMENT '소속 프로젝트 ID',
`name`	VARCHAR(255)	NOT NULL	COMMENT '세그먼트 이름 (예: 서울 VIP 유저)',
`estimated_count`	INT	NULL	COMMENT '예상 대상자 수 -> 하이퍼로그로그',
`created_at`	DATETIME	NOT NULL	COMMENT '생성일시',
`updated_at`	DATETIME	NOT NULL	COMMENT '수정일시'
);

CREATE TABLE `campaigns` (
`campaign_id`	BIGINT	NOT NULL	COMMENT '캠페인 고유 ID',
`member_id`	BIGINT	NOT NULL	COMMENT '생성자 회원 ID',
`project_id`	BIGINT	NOT NULL	COMMENT '소속 프로젝트 ID',
`segment_id`	BIGINT	NULL	COMMENT '세그먼트 ID (NULL이면 전체 발송)',
`name`	VARCHAR(255)	NOT NULL	COMMENT '캠페인 이름',
`channel`	ENUM('PUSH', 'EMAIL')	NOT NULL	DEFAULT 'PUSH'	COMMENT '발송 채널 -> 테이블 분리 필요',
`type`	ENUM('IMMEDIATE', 'SCHEDULED', 'RECURRING')	NOT NULL	COMMENT '발송 방식 (즉시/예약/반복)',
`status`	ENUM('DRAFT', 'SCHEDULED', 'SENDING', 'COMPLETED', 'FAILED')	NOT NULL	DEFAULT 'DRAFT'	COMMENT '캠페인 상태',
`title`	VARCHAR(255)	NOT NULL	COMMENT '알림 제목',
`body`	TEXT	NOT NULL	COMMENT '알림 본문',
`image_url`	VARCHAR(500)	NULL	COMMENT '알림 이미지 URL',
`click_action`	VARCHAR(500)	NULL	COMMENT '클릭 시 이동 URL 또는 딥링크',
`data`	JSON	NULL	COMMENT '커스텀 데이터 (개인화 변수 등)',
`scheduled_at`	DATETIME	NULL	COMMENT '예약 발송 시각',
`recurring_cron`	VARCHAR(50)	NULL	COMMENT '반복 발송 cron 표현식',
`next_execution_at`	DATETIME	NULL	COMMENT '반복 발송 다음 실행 시각',
`send_started_at`	DATETIME	NULL	COMMENT '발송 시작 시각',
`send_completed_at`	DATETIME	NULL	COMMENT '발송 완료 시각',
`version`	BIGINT	NOT NULL	COMMENT '낙관적 락 버전 (중복 발송 방지)',
`created_at`	DATETIME	NOT NULL	COMMENT '생성일시',
`updated_at`	DATETIME	NOT NULL	COMMENT '수정일시'
);

CREATE TABLE `events` (
`event_id`	BIGINT	NOT NULL	COMMENT '이벤트 고유 ID',
`project_id`	BIGINT	NOT NULL	COMMENT '소속 프로젝트 ID',
`user_id`	BIGINT	NULL	COMMENT '이벤트 발생 유저 (로그인 전 NULL)',
`device_id`	BIGINT	NULL	COMMENT '이벤트 발생 기기',
`event_name`	VARCHAR(255)	NOT NULL	COMMENT '이벤트명',
`event_data`	VARCHAR(255)	NULL	COMMENT '이벤트 부가 데이터',
`created_at`	DATETIME	NOT NULL	COMMENT '발생 시각'
);

CREATE TABLE `members` (
`member_id`	BIGINT	NOT NULL	COMMENT '회원고유ID',
`email`	VARCHAR(255)	NOT NULL	COMMENT '로그인 이메일',
`password_hash`	VARCHAR(255)	NOT NULL	COMMENT 'Bcrypt 해시 비밀번호',
`name`	VARCHAR(100)	NOT NULL	COMMENT '회원 이름',
`created_at`	DATETIME	NOT NULL	COMMENT '가입일시'
);

CREATE TABLE `user_tags` (
`user_tag_id`	BIGINT	NOT NULL	COMMENT '유저 태그 고유 ID',
`project_id`	BIGINT	NOT NULL	COMMENT '소속 프로젝트 ID',
`user_id`	BIGINT	NOT NULL	COMMENT '대상 유저 ID',
`tag_key`	VARCHAR(100)	NOT NULL	COMMENT '태그 키 (예: region)',
`tag_value`	VARCHAR(500)	NOT NULL	COMMENT '태그 값 (예: 서울, VIP)',
`created_at`	DATETIME	NOT NULL	COMMENT '생성일시',
`updated_at`	DATETIME	NOT NULL	COMMENT '수정일시'
);

ALTER TABLE `project_members` ADD CONSTRAINT `PK_PROJECT_MEMBERS` PRIMARY KEY (
`project_id`,
`member_id`
);

ALTER TABLE `notification_logs` ADD CONSTRAINT `PK_NOTIFICATION_LOGS` PRIMARY KEY (
`log_id`
);

ALTER TABLE `users` ADD CONSTRAINT `PK_USERS` PRIMARY KEY (
`user_id`
);

ALTER TABLE `campaign_stats` ADD CONSTRAINT `PK_CAMPAIGN_STATS` PRIMARY KEY (
`campaign_id`
);

ALTER TABLE `events_category` ADD CONSTRAINT `PK_EVENTS_CATEGORY` PRIMARY KEY (
`events_category_id`
);

ALTER TABLE `projects` ADD CONSTRAINT `PK_PROJECTS` PRIMARY KEY (
`project_id`
);

ALTER TABLE `segment_rules` ADD CONSTRAINT `PK_SEGMENT_RULES` PRIMARY KEY (
`rule_id`
);

ALTER TABLE `devices` ADD CONSTRAINT `PK_DEVICES` PRIMARY KEY (
`device_id`
);

ALTER TABLE `segments` ADD CONSTRAINT `PK_SEGMENTS` PRIMARY KEY (
`segment_id`
);

ALTER TABLE `campaigns` ADD CONSTRAINT `PK_CAMPAIGNS` PRIMARY KEY (
`campaign_id`
);

ALTER TABLE `events` ADD CONSTRAINT `PK_EVENTS` PRIMARY KEY (
`event_id`
);

ALTER TABLE `members` ADD CONSTRAINT `PK_MEMBERS` PRIMARY KEY (
`member_id`
);

ALTER TABLE `user_tags` ADD CONSTRAINT `PK_USER_TAGS` PRIMARY KEY (
`user_tag_id`
);

ALTER TABLE `project_members` ADD CONSTRAINT `FK_projects_TO_project_members_1` FOREIGN KEY (
`project_id`
)
REFERENCES `projects` (
`project_id`
);

ALTER TABLE `project_members` ADD CONSTRAINT `FK_members_TO_project_members_1` FOREIGN KEY (
`member_id`
)
REFERENCES `members` (
`member_id`
);

ALTER TABLE `campaign_stats` ADD CONSTRAINT `FK_campaigns_TO_campaign_stats_1` FOREIGN KEY (
`campaign_id`
)
REFERENCES `campaigns` (
`campaign_id`
);

