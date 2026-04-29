-- =============================================================================
-- Butterfly CRM — ERD (개정판)
-- 기준: 2026-04-27 / 요구명세서 개정 + ERD 재작성 후 정합성 패치
--
-- 주요 변경 요약
-- 1. campaign_executions: is_deleted/deleted_at/updated_at 추가, FK AUTO_INCREMENT 제거,
--    send_started_at/send_completed_at NULL 허용, created_at DEFAULT 추가
-- 2. 백슬래시 오타 수정 (campaigns.repeat_time, project_stats_daily.stat_id)
-- 3. campaign_execution_status ENUM 중복 'FAIL' 제거
-- 4. Eng 테이블 삭제
-- 5. applications.Field/Field2 → phone/birth_date
-- 6. audit_logs.member_id2 → audit_member_id
-- 7. campaign_stats_daily.bucket_date INT → DATE
-- 8. NOT NULL vs 코멘트 충돌 정리 (events/devices.user_id 코멘트만 정정)
-- 9. campaign_executions.project_id 추가 안 함 (campaign_id 통해 도달 가능)
-- 10. campaign_id로 stats 키 정정 (member_id → campaign_id)
-- 11. segment_snapshots에 campaign_execution_id 추가
-- 12. 회사/유저/세그먼트/이벤트 카테고리/스키마 등 UNIQUE 제약 추가
-- 13. FK 전면 추가, 핵심 인덱스 일괄 추가
-- 14. notification_logs 끝의 익명 Field 컬럼 제거
-- 15. events.event_uuid 추가 (멱등 키, ADR-0010 / docs/decisions/0001)
-- =============================================================================


-- =============================================================================
-- 인증 / 회원 / 신청서 / 프로젝트
-- =============================================================================

CREATE TABLE `members` (
    `member_id`     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '회원 고유 ID',
    `project_id`    BIGINT       NULL                     COMMENT 'ADMIN / OWNER 생성 시 NULL 가능',
    `email`         VARCHAR(255) NOT NULL                 COMMENT '로그인 이메일',
    `password_hash` VARCHAR(255) NULL                     COMMENT 'BCrypt 해시 비밀번호 (운영진이 계정 생성 시 NULL 가능)',
    `name`          VARCHAR(20)  NOT NULL                 COMMENT '회원 이름',
    `phone`         VARCHAR(11)  NOT NULL                 COMMENT '전화번호',
    `birth_date`    DATE         NOT NULL                 COMMENT '생년월일 (YYYY-MM-DD)',
    `role_type`     ENUM('ADMIN', 'OWNER', 'EDITOR', 'VIEWER') NOT NULL DEFAULT 'VIEWER'
                                                          COMMENT 'ADMIN=CRM 운영진, OWNER/EDITOR/VIEWER=플랫폼 사용자',
    `is_deleted`    BOOLEAN      NOT NULL DEFAULT FALSE   COMMENT '소프트 딜리트 플래그',
    `deleted_at`    DATETIME     NULL                     COMMENT '탈퇴 일시',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입 일시',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시'
);

CREATE TABLE `applications` (
    `application_id` BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '신청서 ID',
    `applicant_name` VARCHAR(100) NOT NULL                 COMMENT '신청자 이름',
    `email`          VARCHAR(255) NOT NULL                 COMMENT '신청자 이메일',
    `phone`          VARCHAR(11)  NOT NULL                 COMMENT '신청자 전화번호',
    `birth_date`     DATE         NOT NULL                 COMMENT '신청자 생년월일',
    `business_name`  VARCHAR(255) NOT NULL                 COMMENT '사업자 명',
    `business_no`    VARCHAR(50)  NOT NULL                 COMMENT '사업자 번호',
    `status`         ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '신청 상태',
    `reject_reason`  TEXT         NULL                     COMMENT '반려 사유',
    `reviewed_by`    BIGINT       NULL                     COMMENT '검토한 ADMIN member_id',
    `reviewed_at`    DATETIME     NULL                     COMMENT '검토 완료 시각',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 일시',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시'
);

CREATE TABLE `projects` (
    `project_id`       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '프로젝트 고유 ID',
    `name`             VARCHAR(255) NOT NULL                 COMMENT '프로젝트/회사명',
    `business_no`      VARCHAR(50)  NOT NULL                 COMMENT '사업자 번호',
    `company_auth_no`  VARCHAR(100) NOT NULL                 COMMENT '회사 인증 번호 (멤버 가입용)',
    `owner_id`         BIGINT       NOT NULL                 COMMENT 'OWNER 회원 ID',
    `public_api_key`   VARCHAR(100) NULL                     COMMENT 'SDK용 공개 키 (pk_live_xxx)',
    `fcm_credentials`  TEXT         NULL                     COMMENT 'Firebase 서비스 계정 JSON (AES 암호화)',
    `daily_limit`      INT          NULL                     COMMENT '유저당 일일 발송 한도',
    `plan`             ENUM('FREE', 'BASIC', 'PRO', 'ENTERPRISE') NOT NULL DEFAULT 'FREE' COMMENT '프로젝트 플랜',
    `is_deleted`       BOOLEAN      NOT NULL DEFAULT FALSE   COMMENT '소프트 딜리트 플래그',
    `deleted_at`       DATETIME     NULL                     COMMENT '삭제 일시',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시'
);

CREATE TABLE `refresh_tokens` (
    `token_id`   BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '토큰 고유 ID',
    `member_id`  BIGINT       NOT NULL                 COMMENT '회원 ID',
    `token`      VARCHAR(500) NOT NULL                 COMMENT 'Refresh Token 값',
    `expires_at` DATETIME     NOT NULL                 COMMENT '만료 시각',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 시각'
);

CREATE TABLE `token_blacklist` (
    `blacklist_id` BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '블랙리스트 ID',
    `token`        VARCHAR(500) NOT NULL                 COMMENT 'jti (JWT ID) — Access Token 무효화 키',
    `expires_at`   DATETIME     NOT NULL                 COMMENT '원래 만료 시각 (이후 정리 배치 대상)',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '블랙리스트 등록 시각'
);

CREATE TABLE `audit_logs` (
    `audit_id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '감사 로그 ID',
    `project_id`       BIGINT       NULL                     COMMENT '프로젝트 ID (시스템 레벨 시 NULL)',
    `member_id`        BIGINT       NOT NULL                 COMMENT '작업 수행 회원 ID',
    `action`           VARCHAR(100) NOT NULL                 COMMENT '작업 종류 (API_KEY_ISSUED, API_KEY_REVOKED, FCM_CREDENTIAL_UPDATED, MEMBER_ROLE_CHANGED 등 — AuditAction 상수)',
    `target_type`      VARCHAR(50)  NOT NULL                 COMMENT '대상 엔티티 타입',
    `target_id`        BIGINT       NULL                     COMMENT '대상 엔티티 ID',
    `detail`           JSON         NULL                     COMMENT '추가 상세 (변경 전/후 값 등)',
    `user_agent`       VARCHAR(500) NULL                     COMMENT 'User-Agent',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작업 시각'
);


-- =============================================================================
-- 고객 / 기기 / 이벤트
-- =============================================================================

CREATE TABLE `users` (
    `user_id`      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '유저 고유 ID',
    `project_id`   BIGINT       NOT NULL                 COMMENT '소속 프로젝트 ID',
    `external_id`  VARCHAR(255) NOT NULL                 COMMENT '고객사의 유저 식별자',
    `email`        VARCHAR(255) NULL                     COMMENT '유저 이메일 (이메일 발송용)',
    `phone`        VARCHAR(20)  NULL                     COMMENT '유저 전화번호 (SMS 발송용)',
    `birth_date`   DATE         NULL                     COMMENT '생년월일 (세그먼트 필터용)',
    `push_opt_in` BOOLEAN       NOT NULL DEFAULT TRUE    COMMENT '푸시 수신 동의 여부',
    `signed_up_at` DATETIME     NULL                     COMMENT '가입 시점',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 수집 일시',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시'
);

CREATE TABLE `devices` (
    `device_id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '기기 고유 ID',
    `project_id`            BIGINT       NOT NULL                 COMMENT '소속 프로젝트 ID',
    `user_id`               BIGINT       NOT NULL                 COMMENT '유저 ID',
    `platform`              ENUM('ANDROID', 'IOS', 'WEB') NOT NULL COMMENT '플랫폼',
    `push_token`            VARCHAR(500) NOT NULL                 COMMENT 'FCM 푸시 토큰',
    `push_token_updated_at` DATETIME     NOT NULL                 COMMENT '토큰 갱신 시각',
    `token_valid`           BOOLEAN      NOT NULL DEFAULT TRUE    COMMENT '토큰 유효 여부 (4xx시 FALSE)',
    `app_version`           VARCHAR(50)  NOT NULL                 COMMENT '앱 버전',
    `os_version`            VARCHAR(50)  NOT NULL                 COMMENT 'OS 버전',
    `last_active_at`        DATETIME     NULL                     COMMENT '마지막 접속 시각',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 일시'
);

CREATE TABLE `events` (
    `event_id`       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '이벤트 고유 ID (PK)',
    `event_uuid`     VARCHAR(36)  NOT NULL                 COMMENT '멱등 키 (API 서버 부여 UUID, ADR-0010/decisions/0001)',
    `project_id`     BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `user_id`        BIGINT       NOT NULL                 COMMENT '유저 ID',
    `category_id`    BIGINT       NOT NULL                 COMMENT '이벤트 카테고리 ID',
    `device_id`      BIGINT       NOT NULL                 COMMENT '기기 ID',
    `occurred_at`    DATETIME(3)  NOT NULL                 COMMENT '이벤트 발생 시각 (ms 단위)',
    `schema_version` INT          NOT NULL DEFAULT 1       COMMENT '수집 당시 스키마 버전',
    `event_data`     JSON         NULL                     COMMENT '이벤트 부가 데이터',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'DB 수집 시각',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 시각 (BaseEntity 컨벤션, 실질 immutable)'
);

CREATE TABLE `event_categories` (
    `category_id` BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '이벤트 카테고리 ID',
    `project_id`  BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `name`        VARCHAR(100) NOT NULL                 COMMENT '이벤트명 (예: purchase, login)',
    `type`        ENUM('DEFAULT', 'CUSTOM') NOT NULL DEFAULT 'CUSTOM' COMMENT '기본/커스텀 구분',
    `description` VARCHAR(500) NULL                     COMMENT '설명',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 시각'
);

CREATE TABLE `event_schemas` (
    `schema_id`      BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '스키마 이력 ID',
    `project_id`     BIGINT   NOT NULL                 COMMENT '프로젝트 ID',
    `category_id`    BIGINT   NOT NULL                 COMMENT '이벤트 카테고리 ID',
    `version`        INT      NOT NULL                 COMMENT '스키마 버전 (1, 2, 3...)',
    `schema_def`     JSON     NOT NULL                 COMMENT '속성 정의 + 이전 이름 매핑',
    `effective_from` DATETIME NOT NULL                 COMMENT '이 버전이 적용되기 시작한 시각',
    `is_current`     BOOLEAN  NOT NULL DEFAULT FALSE   COMMENT '현재 활성 버전 여부',
    `created_by`     BIGINT   NOT NULL                 COMMENT '변경한 member_id',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
);

CREATE TABLE `event_schema_fields` (
    `field_id`       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '필드 ID',
    `schema_id`      BIGINT       NOT NULL                 COMMENT '스키마 이력 ID',
    `field_name`     VARCHAR(100) NOT NULL                 COMMENT '속성명 (예: product_color)',
    `field_type`     ENUM('STRING', 'NUMBER', 'BOOLEAN', 'DATE') NOT NULL DEFAULT 'STRING' COMMENT '속성 타입',
    `is_required`    BOOLEAN      NOT NULL DEFAULT FALSE   COMMENT '필수 여부',
    `is_promoted`    BOOLEAN      NOT NULL DEFAULT FALSE   COMMENT 'generated column 인덱스 승격 여부',
    `previous_names` JSON         NULL                     COMMENT '이전 이름 목록 (예: ["color"])',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
);

CREATE TABLE `event_daily_agg` (
    `log_date`    DATE        NOT NULL                 COMMENT '집계 일자',
    `project_id`  BIGINT      NOT NULL                 COMMENT '프로젝트 ID',
    `category_id` BIGINT      NOT NULL                 COMMENT '이벤트 카테고리 ID',
    `user_id`     BIGINT      NOT NULL                 COMMENT '유저 고유 ID',
    `event_count` INT         NOT NULL DEFAULT 0       COMMENT '해당 일자 이벤트 발생 수',
    `first_at`    DATETIME(3) NOT NULL                 COMMENT '해당 일자 최초 발생 시각',
    `last_at`     DATETIME(3) NOT NULL                 COMMENT '해당 일자 최종 발생 시각',
    `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최종 갱신 시각'
);

CREATE TABLE `tag_definitions` (
    `tag_def_id`   BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '태그 정의 고유 ID',
    `project_id`   BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `tag_key`      VARCHAR(100) NOT NULL                 COMMENT '태그 키 (예: region)',
    `display_name` VARCHAR(100) NOT NULL                 COMMENT '대시보드 표시명 (예: 거주지역)',
    `tag_type`     ENUM('STRING', 'NUMBER', 'BOOLEAN') NOT NULL DEFAULT 'STRING' COMMENT '태그 값 타입',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
);

CREATE TABLE `user_tags` (
    `user_tag_id` BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '사용자 태그 ID',
    `project_id`  BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `user_id`     BIGINT       NOT NULL                 COMMENT '유저 ID',
    `tag_key`     VARCHAR(100) NOT NULL                 COMMENT '태그 키 (예: region)',
    `tag_value`   VARCHAR(500) NOT NULL                 COMMENT '태그 값 (예: 서울, VIP)',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시'
);


-- =============================================================================
-- 세그먼트
-- =============================================================================

CREATE TABLE `segments` (
    `segment_id`      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '세그먼트 고유 ID',
    `project_id`      BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `name`            VARCHAR(30)  NOT NULL                 COMMENT '세그먼트명 (중복 불가, 30자)',
    `description`     VARCHAR(100) NULL                     COMMENT '세그먼트 설명 (100자)',
    `condition_tree`  JSON         NOT NULL                 COMMENT 'AND/OR 조건 트리 구조',
    `schema_version`  INT          NOT NULL DEFAULT 1       COMMENT '세그먼트 생성 당시 스키마 버전',
    `estimated_count` INT          NULL DEFAULT 0           COMMENT '예상 대상자 수 (캐시)',
    `estimated_at`    DATETIME     NULL                     COMMENT '모수 계산 시각',
    `last_built_at`   DATETIME     NULL                     COMMENT 'segment_members 마지막 빌드 시각',
    `build_status`    ENUM('PENDING', 'BUILDING', 'READY', 'FAILED') NOT NULL DEFAULT 'PENDING'
                                                            COMMENT 'Materialized Segment 빌드 상태',
    `created_by`      BIGINT       NOT NULL                 COMMENT '생성자 member_id',
    `is_deleted`      BOOLEAN      NOT NULL DEFAULT FALSE   COMMENT '소프트 딜리트',
    `deleted_at`      DATETIME     NULL                     COMMENT '삭제 일시',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시'
);

CREATE TABLE `segment_members` (
    `segment_id` BIGINT      NOT NULL                 COMMENT '세그먼트 ID',
    `user_id`    BIGINT      NOT NULL                 COMMENT '유저 고유 ID',
    `matched_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '편입 시각',
    `expires_at` DATETIME    NULL                     COMMENT 'TTL 기반 이탈 시각 (선택적)'
);

CREATE TABLE `segment_favorites` (
    `segment_id` BIGINT   NOT NULL                 COMMENT '세그먼트 ID',
    `member_id`  BIGINT   NOT NULL                 COMMENT '회원 ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '즐겨찾기 등록 일시'
);

-- 회차별 세그먼트 모수 캡처 (캠페인 회차 단위로 1:1)
CREATE TABLE `segment_snapshots` (
    `snapshot_id`           BIGINT NOT NULL AUTO_INCREMENT  COMMENT '스냅샷 ID',
    `segment_id`            BIGINT NOT NULL                 COMMENT '세그먼트 ID',
    `campaign_id`           BIGINT NOT NULL                 COMMENT '캠페인 ID',
    `campaign_execution_id` BIGINT NULL                     COMMENT '캠페인 실행 회차 ID (반복 캠페인 회차별 모수 추적)',
    `target_count`          INT    NOT NULL DEFAULT 0       COMMENT '스냅샷 대상자 수',
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '스냅샷 생성 시각'
);

CREATE TABLE `segment_snapshot_users` (
    `snapshot_id` BIGINT NOT NULL COMMENT '스냅샷 ID',
    `user_id`     BIGINT NOT NULL COMMENT '유저 고유 ID'
);


-- =============================================================================
-- 캠페인 / 메시지 템플릿
-- =============================================================================

CREATE TABLE `message_templates` (
    `message_template_id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '템플릿 고유 ID',
    `member_id`                      BIGINT       NOT NULL                 COMMENT '생성자 회원 ID',
    `project_id`                     BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `message_templates_name`         VARCHAR(255) NOT NULL                 COMMENT '템플릿 이름',
    `message_templates_description`  VARCHAR(500) NOT NULL                 COMMENT '템플릿 설명',
    `channel`                        ENUM('PUSH', 'EMAIL', 'SMS', 'WEBHOOK', 'KAKAO') NOT NULL DEFAULT 'PUSH' COMMENT '발송 채널',
    `message_templates_title`        VARCHAR(255) NOT NULL                 COMMENT '메시지 제목 ({{name}} 변수 지원)',
    `message_templates_body`         TEXT         NOT NULL                 COMMENT '메시지 본문 ({{name}} 변수 지원)',
    `message_templates_image_url`    VARCHAR(500) NULL                     COMMENT '알림 이미지 URL',
    `is_deleted`                     BOOLEAN      NOT NULL DEFAULT FALSE   COMMENT '소프트 딜리트',
    `deleted_at`                     DATETIME     NULL                     COMMENT '삭제 일시',
    `created_at`                     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at`                     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시'
);

CREATE TABLE `campaigns` (
    `campaign_id`                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '캠페인 고유 ID',
    `project_id`                 BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `message_template_id`        BIGINT       NOT NULL                 COMMENT '템플릿 ID',
    `segment_id`                 BIGINT       NULL                     COMMENT '대상 세그먼트 ID (NULL=전체 발송)',
    `member_id`                  BIGINT       NOT NULL                 COMMENT '생성자 회원 ID',
    `campaign_name`              VARCHAR(255) NOT NULL                 COMMENT '캠페인 이름',
    `campaign_description`       VARCHAR(500) NULL                     COMMENT '캠페인 설명',
    `campaign_type`              ENUM('ONE_TIME', 'AUTOMATION') NOT NULL DEFAULT 'ONE_TIME' COMMENT '캠페인 유형 (단발/자동화)',
    `channel`                    ENUM('PUSH', 'EMAIL', 'SMS', 'WEBHOOK', 'KAKAO') NOT NULL DEFAULT 'PUSH' COMMENT '발송 채널',
    `campaign_status`            ENUM('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED') NOT NULL DEFAULT 'DRAFT' COMMENT '캠페인 상태',
    `recurring_cron`             VARCHAR(50)  NULL                     COMMENT '반복 발송 cron 표현식 (정규화 컬럼과 동시 사용 안 함)',
    `repeat_type`                ENUM('DAILY', 'WEEKLY', 'MONTHLY') NULL COMMENT '필터링 및 통계 기준점',
    `repeat_day_of_week`         VARCHAR(50)  NULL                     COMMENT '주간 반복 시 요일 (예: MON,WED,FRI)',
    `repeat_day_of_month`        INT          NULL                     COMMENT '월간 반복 시 날짜 (1-31)',
    `repeat_time`                TIME         NULL                     COMMENT '발송 시각 (예: 15:00:00)',
    `recurring_count`            INT          NULL                     COMMENT '목표 반복 발송 횟수 (단발성은 1)',
    `recurring_execution_count`  INT          NOT NULL DEFAULT 0       COMMENT '현재까지 스케줄러가 발송 시도한 횟수',
    `next_run_at`                DATETIME     NULL                     COMMENT '다음 발송 예정 시각 (스케줄러 풀스캔 방지 인덱스)',
    `version`                    INT          NOT NULL DEFAULT 0       COMMENT '낙관적 락 버전 (중복 발송 방지)',
    `is_deleted`                 BOOLEAN      NOT NULL DEFAULT FALSE   COMMENT '소프트 딜리트',
    `deleted_at`                 DATETIME     NULL                     COMMENT '삭제 일시',
    `created_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시'
);

-- 회차별 발송 이력 (반복 캠페인 회차 단위로 1행)
CREATE TABLE `campaign_executions` (
    `campaign_execution_id`      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '실행 ID',
    `campaign_id`                BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `run_round`                  INT          NOT NULL                 COMMENT '발송 회차',
    `campaign_execution_status`  ENUM('READY', 'SEND', 'SUCCESS', 'FAIL') NOT NULL COMMENT '회차 발송 상태',
    `send_started_at`            DATETIME     NULL                     COMMENT '회차 발송 시작 시각 (큐 적재 시각)',
    `send_completed_at`          DATETIME     NULL                     COMMENT '회차 발송 완료 시각',
    `target_count`               INT          NOT NULL DEFAULT 0       COMMENT '해당 회차 총 발송 대상자 수',
    `sent_count`                 INT          NOT NULL DEFAULT 0       COMMENT '실제 발송 성공 수',
    `failed_count`               INT          NOT NULL DEFAULT 0       COMMENT '발송 실패 수',
    `fail_reason`                VARCHAR(500) NULL                     COMMENT '회차 실패 사유',
    `is_deleted`                 BOOLEAN      NOT NULL DEFAULT FALSE   COMMENT '소프트 딜리트',
    `deleted_at`                 DATETIME     NULL                     COMMENT '삭제 일시',
    `created_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '실행 이력 생성 시각',
    `updated_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 시각'
);


-- =============================================================================
-- 발송 / 사용자 행동
-- =============================================================================

CREATE TABLE `notification_logs` (
    `log_id`        BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '로그 고유 ID',
    `project_id`    BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`   BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `user_id`       BIGINT       NULL                     COMMENT '유저 ID',
    `device_id`     BIGINT       NULL                     COMMENT '기기 ID',
    `channel`       ENUM('PUSH', 'EMAIL', 'SMS', 'WEBHOOK', 'KAKAO') NOT NULL COMMENT '발송 채널',
    `status`        ENUM('PENDING', 'SENT', 'DELIVERED', 'CLICKED', 'FAILED') NOT NULL COMMENT '발송 상태',
    `error_code`    VARCHAR(50)  NULL                     COMMENT '에러 코드',
    `error_message` TEXT         NULL                     COMMENT '에러 메시지',
    `sent_at`       DATETIME     NULL                     COMMENT '발송 시각',
    `delivered_at`  DATETIME     NULL                     COMMENT '도달 시각',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '로그 생성 시각'
);

CREATE TABLE `user_action_logs` (
    `action_id`      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '액션 고유 ID',
    `project_id`     BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`    BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `log_id`         BIGINT       NOT NULL                 COMMENT 'notification_logs.log_id',
    `user_id`        BIGINT       NOT NULL                 COMMENT '유저 ID',
    `device_id`      BIGINT       NOT NULL                 COMMENT '기기 ID',
    `action_type`    ENUM('OPEN','CLICK','CONVERT','DISMISS') NOT NULL COMMENT '행동 종류',
    `action_target`  VARCHAR(500) NULL                     COMMENT '클릭한 링크 URL 등',
    `user_agent`     VARCHAR(500) NULL                     COMMENT 'User-Agent',
    `action_at`      DATETIME     NOT NULL                 COMMENT '행동 발생 시각',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '로그 생성 시각'
);


-- =============================================================================
-- 분석 / 집계 / 운영 모니터링
-- =============================================================================

CREATE TABLE `campaign_stats_daily` (
    `stat_id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`      BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`     BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `channel`         ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `bucket_date`     DATE         NOT NULL                 COMMENT '일 버킷',
    `sent_count`      INT          NOT NULL DEFAULT 0       COMMENT '발송 수',
    `delivered_count` INT          NOT NULL DEFAULT 0       COMMENT '도달 수',
    `failed_count`    INT          NOT NULL DEFAULT 0       COMMENT '실패 수',
    `open_count`      INT          NOT NULL DEFAULT 0       COMMENT '오픈 수',
    `click_count`     INT          NOT NULL DEFAULT 0       COMMENT '클릭 수',
    `convert_count`   INT          NOT NULL DEFAULT 0       COMMENT '전환 수',
    `delivery_rate`   DECIMAL(5,2) NULL                     COMMENT '도달률 (%)',
    `open_rate`       DECIMAL(5,2) NULL                     COMMENT '오픈률 (%)',
    `click_rate`      DECIMAL(5,2) NULL                     COMMENT '클릭률 (%)',
    `convert_rate`    DECIMAL(5,2) NULL                     COMMENT '전환률 (%)',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 시각'
);

CREATE TABLE `campaign_stats_hourly` (
    `stat_id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`      BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`     BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `channel`         ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `bucket_hour`     DATETIME     NOT NULL                 COMMENT '시간 버킷 (예: 2026-04-24 15:00:00)',
    `pending_count`   INT          NOT NULL DEFAULT 0       COMMENT '대기 수',
    `sent_count`      INT          NOT NULL DEFAULT 0       COMMENT '발송 수',
    `delivered_count` INT          NOT NULL DEFAULT 0       COMMENT '도달 수',
    `failed_count`    INT          NOT NULL DEFAULT 0       COMMENT '실패 수',
    `open_count`      INT          NOT NULL DEFAULT 0       COMMENT '오픈 수',
    `click_count`     INT          NOT NULL DEFAULT 0       COMMENT '클릭 수',
    `convert_count`   INT          NOT NULL DEFAULT 0       COMMENT '전환 수',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 시각'
);

CREATE TABLE `template_stats_daily` (
    `stat_id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`          BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `template_id`         BIGINT       NOT NULL                 COMMENT '템플릿 ID',
    `channel`             ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `bucket_date`         DATE         NOT NULL                 COMMENT '일 버킷',
    `used_campaign_count` INT          NOT NULL DEFAULT 0       COMMENT '이 템플릿 사용 캠페인 수',
    `sent_count`          INT          NOT NULL DEFAULT 0       COMMENT '발송 수',
    `delivered_count`     INT          NOT NULL DEFAULT 0       COMMENT '도달 수',
    `open_count`          INT          NOT NULL DEFAULT 0       COMMENT '오픈 수',
    `click_count`         INT          NOT NULL DEFAULT 0       COMMENT '클릭 수',
    `convert_count`       INT          NOT NULL DEFAULT 0       COMMENT '전환 수',
    `open_rate`           DECIMAL(5,2) NULL                     COMMENT '오픈률 (%)',
    `click_rate`          DECIMAL(5,2) NULL                     COMMENT '클릭률 (%)',
    `convert_rate`        DECIMAL(5,2) NULL                     COMMENT '전환률 (%)',
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 시각'
);

CREATE TABLE `failure_stats_daily` (
    `stat_id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`           BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `channel`              ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `error_code`           VARCHAR(50)  NOT NULL                 COMMENT 'INVALID_TOKEN, AUTH_ERROR 등',
    `bucket_date`          DATE         NOT NULL                 COMMENT '일 버킷',
    `failed_count`         INT          NOT NULL DEFAULT 0       COMMENT '실패 건수',
    `affected_campaign_cnt` INT         NOT NULL DEFAULT 0       COMMENT '영향 받은 캠페인 수',
    `affected_user_cnt`    INT          NOT NULL DEFAULT 0       COMMENT '영향 받은 유저 수',
    `first_occurred_at`    DATETIME     NULL                     COMMENT '해당일 최초 발생 시각',
    `last_occurred_at`     DATETIME     NULL                     COMMENT '해당일 마지막 발생 시각',
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 시각'
);

CREATE TABLE `project_stats_daily` (
    `stat_id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`            BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `bucket_date`           DATE         NOT NULL                 COMMENT '일 버킷',
    `active_user_count`     INT          NOT NULL DEFAULT 0       COMMENT '당일 이벤트 발생 유저 수',
    `new_user_count`        INT          NOT NULL DEFAULT 0       COMMENT '신규 가입 유저 수',
    `churn_user_count`      INT          NOT NULL DEFAULT 0       COMMENT '이탈 세그먼트 매칭 수',
    `total_sent`            INT          NOT NULL DEFAULT 0       COMMENT '총 발송 수',
    `total_delivered`       INT          NOT NULL DEFAULT 0       COMMENT '총 도달 수',
    `total_failed`          INT          NOT NULL DEFAULT 0       COMMENT '총 실패 수',
    `total_open`            INT          NOT NULL DEFAULT 0       COMMENT '총 오픈 수',
    `total_click`           INT          NOT NULL DEFAULT 0       COMMENT '총 클릭 수',
    `total_convert`         INT          NOT NULL DEFAULT 0       COMMENT '총 전환 수',
    `push_open_rate`        DECIMAL(5,2) NULL                     COMMENT '앱 푸시 오픈률 (%)',
    `overall_click_rate`    DECIMAL(5,2) NULL                     COMMENT '전체 클릭률 (%)',
    `overall_convert_rate`  DECIMAL(5,2) NULL                     COMMENT '전체 전환률 (%)',
    `active_campaign_count` INT          NOT NULL DEFAULT 0       COMMENT '활성 캠페인 수',
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 시각'
);

CREATE TABLE `system_health_snapshots` (
    `snapshot_id`     BIGINT         NOT NULL AUTO_INCREMENT  COMMENT '스냅샷 ID',
    `metric_name`     VARCHAR(50)    NOT NULL                 COMMENT 'kafka_lag, worker_tps 등',
    `metric_target`   VARCHAR(100)   NULL                     COMMENT 'topic명, worker명 등',
    `metric_value`    DECIMAL(18,4)  NOT NULL                 COMMENT '측정값',
    `threshold_warn`  DECIMAL(18,4)  NULL                     COMMENT '경고 임계치',
    `threshold_crit`  DECIMAL(18,4)  NULL                     COMMENT '위험 임계치',
    `status`          ENUM('OK','WARN','CRITICAL') NOT NULL   COMMENT '상태',
    `collected_at`    DATETIME       NOT NULL                 COMMENT '수집 시각'
);

CREATE TABLE `send_reconciliations` (
    `recon_id`       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '검증 ID',
    `project_id`     BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`    BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `channel`        ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `expected_count` INT          NOT NULL                 COMMENT '세그먼트 스냅샷 대상자 수',
    `actual_count`   INT          NOT NULL                 COMMENT '실제 기록된 수',
    `sent_count`     INT          NOT NULL DEFAULT 0       COMMENT 'SENT 상태 수',
    `failed_count`   INT          NOT NULL DEFAULT 0       COMMENT 'FAILED 상태 수',
    `diff_count`     INT          NOT NULL                 COMMENT 'expected - actual',
    `is_consistent`  BOOLEAN      NOT NULL                 COMMENT 'diff == 0',
    `note`           VARCHAR(500) NULL                     COMMENT '비고',
    `checked_at`     DATETIME     NOT NULL                 COMMENT '검증 수행 시각',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각'
);


-- =============================================================================
-- PRIMARY KEY
-- =============================================================================

ALTER TABLE `members`                ADD CONSTRAINT `PK_MEMBERS`                PRIMARY KEY (`member_id`);
ALTER TABLE `applications`           ADD CONSTRAINT `PK_APPLICATIONS`           PRIMARY KEY (`application_id`);
ALTER TABLE `projects`               ADD CONSTRAINT `PK_PROJECTS`               PRIMARY KEY (`project_id`);
ALTER TABLE `refresh_tokens`         ADD CONSTRAINT `PK_REFRESH_TOKENS`         PRIMARY KEY (`token_id`);
ALTER TABLE `token_blacklist`        ADD CONSTRAINT `PK_TOKEN_BLACKLIST`        PRIMARY KEY (`blacklist_id`);
ALTER TABLE `audit_logs`             ADD CONSTRAINT `PK_AUDIT_LOGS`             PRIMARY KEY (`audit_id`);
ALTER TABLE `users`                  ADD CONSTRAINT `PK_USERS`                  PRIMARY KEY (`user_id`);
ALTER TABLE `devices`                ADD CONSTRAINT `PK_DEVICES`                PRIMARY KEY (`device_id`);
ALTER TABLE `events`                 ADD CONSTRAINT `PK_EVENTS`                 PRIMARY KEY (`event_id`);
ALTER TABLE `event_categories`       ADD CONSTRAINT `PK_EVENT_CATEGORIES`       PRIMARY KEY (`category_id`);
ALTER TABLE `event_schemas`          ADD CONSTRAINT `PK_EVENT_SCHEMAS`          PRIMARY KEY (`schema_id`);
ALTER TABLE `event_schema_fields`    ADD CONSTRAINT `PK_EVENT_SCHEMA_FIELDS`    PRIMARY KEY (`field_id`);
ALTER TABLE `event_daily_agg`        ADD CONSTRAINT `PK_EVENT_DAILY_AGG`        PRIMARY KEY (`log_date`,`project_id`,`category_id`,`user_id`);
ALTER TABLE `tag_definitions`        ADD CONSTRAINT `PK_TAG_DEFINITIONS`        PRIMARY KEY (`tag_def_id`);
ALTER TABLE `user_tags`              ADD CONSTRAINT `PK_USER_TAGS`              PRIMARY KEY (`user_tag_id`);
ALTER TABLE `segments`               ADD CONSTRAINT `PK_SEGMENTS`               PRIMARY KEY (`segment_id`);
ALTER TABLE `segment_members`        ADD CONSTRAINT `PK_SEGMENT_MEMBERS`        PRIMARY KEY (`segment_id`,`user_id`);
ALTER TABLE `segment_favorites`      ADD CONSTRAINT `PK_SEGMENT_FAVORITES`      PRIMARY KEY (`segment_id`,`member_id`);
ALTER TABLE `segment_snapshots`      ADD CONSTRAINT `PK_SEGMENT_SNAPSHOTS`      PRIMARY KEY (`snapshot_id`);
ALTER TABLE `segment_snapshot_users` ADD CONSTRAINT `PK_SEGMENT_SNAPSHOT_USERS` PRIMARY KEY (`snapshot_id`,`user_id`);
ALTER TABLE `message_templates`      ADD CONSTRAINT `PK_MESSAGE_TEMPLATES`      PRIMARY KEY (`message_template_id`);
ALTER TABLE `campaigns`              ADD CONSTRAINT `PK_CAMPAIGNS`              PRIMARY KEY (`campaign_id`);
ALTER TABLE `campaign_executions`    ADD CONSTRAINT `PK_CAMPAIGN_EXECUTIONS`    PRIMARY KEY (`campaign_execution_id`);
ALTER TABLE `notification_logs`      ADD CONSTRAINT `PK_NOTIFICATION_LOGS`      PRIMARY KEY (`log_id`);
ALTER TABLE `user_action_logs`       ADD CONSTRAINT `PK_USER_ACTION_LOGS`       PRIMARY KEY (`action_id`);
ALTER TABLE `campaign_stats_daily`   ADD CONSTRAINT `PK_CAMPAIGN_STATS_DAILY`   PRIMARY KEY (`stat_id`);
ALTER TABLE `campaign_stats_hourly`  ADD CONSTRAINT `PK_CAMPAIGN_STATS_HOURLY`  PRIMARY KEY (`stat_id`);
ALTER TABLE `template_stats_daily`   ADD CONSTRAINT `PK_TEMPLATE_STATS_DAILY`   PRIMARY KEY (`stat_id`);
ALTER TABLE `failure_stats_daily`    ADD CONSTRAINT `PK_FAILURE_STATS_DAILY`    PRIMARY KEY (`stat_id`);
ALTER TABLE `project_stats_daily`    ADD CONSTRAINT `PK_PROJECT_STATS_DAILY`    PRIMARY KEY (`stat_id`);
ALTER TABLE `system_health_snapshots` ADD CONSTRAINT `PK_SYSTEM_HEALTH_SNAPSHOTS` PRIMARY KEY (`snapshot_id`);
ALTER TABLE `send_reconciliations`   ADD CONSTRAINT `PK_SEND_RECONCILIATIONS`   PRIMARY KEY (`recon_id`);


-- =============================================================================
-- UNIQUE 제약
-- =============================================================================

-- 인증 / 회원 / 신청서
ALTER TABLE `members`         ADD CONSTRAINT `UK_MEMBERS_EMAIL`            UNIQUE (`email`);
ALTER TABLE `refresh_tokens`  ADD CONSTRAINT `UK_REFRESH_TOKENS_TOKEN`     UNIQUE (`token`);
ALTER TABLE `token_blacklist` ADD CONSTRAINT `UK_TOKEN_BLACKLIST_TOKEN`    UNIQUE (`token`);

-- 프로젝트
ALTER TABLE `projects`        ADD CONSTRAINT `UK_PROJECTS_BUSINESS_NO`     UNIQUE (`business_no`);
ALTER TABLE `projects`        ADD CONSTRAINT `UK_PROJECTS_PUBLIC_API_KEY`  UNIQUE (`public_api_key`);
ALTER TABLE `projects`        ADD CONSTRAINT `UK_PROJECTS_COMPANY_AUTH_NO` UNIQUE (`company_auth_no`);

-- 고객 / 이벤트
ALTER TABLE `users`             ADD CONSTRAINT `UK_USERS_PROJECT_EXTID` UNIQUE (`project_id`,`external_id`);
ALTER TABLE `event_categories`  ADD CONSTRAINT `UK_EVENT_CATEGORIES_PROJECT_NAME` UNIQUE (`project_id`,`name`);
ALTER TABLE `events`             ADD CONSTRAINT `UK_EVENTS_EVENT_UUID`             UNIQUE (`event_uuid`);
ALTER TABLE `event_schemas`     ADD CONSTRAINT `UK_EVENT_SCHEMAS_PROJECT_CAT_VER` UNIQUE (`project_id`,`category_id`,`version`);
ALTER TABLE `tag_definitions`   ADD CONSTRAINT `UK_TAG_DEFINITIONS_PROJECT_KEY`   UNIQUE (`project_id`,`tag_key`);
ALTER TABLE `user_tags`         ADD CONSTRAINT `UK_USER_TAGS_PROJECT_USER_KEY`    UNIQUE (`project_id`,`user_id`,`tag_key`);

-- 세그먼트 (이름 중복 불가 — 요구명세서)
ALTER TABLE `segments` ADD CONSTRAINT `UK_SEGMENTS_PROJECT_NAME_DELETED` UNIQUE (`project_id`,`name`,`is_deleted`);

-- 캠페인 회차 (캠페인 내 회차 번호 유일)
ALTER TABLE `campaign_executions` ADD CONSTRAINT `UK_CAMPAIGN_EXECUTIONS_CAMPAIGN_ROUND` UNIQUE (`campaign_id`,`run_round`);

-- 분석 집계 테이블 (자연 키 중복 방지)
ALTER TABLE `campaign_stats_daily`  ADD CONSTRAINT `UK_CAMPAIGN_STATS_DAILY_NAT`  UNIQUE (`campaign_id`,`channel`,`bucket_date`);
ALTER TABLE `campaign_stats_hourly` ADD CONSTRAINT `UK_CAMPAIGN_STATS_HOURLY_NAT` UNIQUE (`campaign_id`,`channel`,`bucket_hour`);
ALTER TABLE `template_stats_daily`  ADD CONSTRAINT `UK_TEMPLATE_STATS_DAILY_NAT`  UNIQUE (`template_id`,`channel`,`bucket_date`);
ALTER TABLE `failure_stats_daily`   ADD CONSTRAINT `UK_FAILURE_STATS_DAILY_NAT`   UNIQUE (`project_id`,`channel`,`error_code`,`bucket_date`);
ALTER TABLE `project_stats_daily`   ADD CONSTRAINT `UK_PROJECT_STATS_DAILY_NAT`   UNIQUE (`project_id`,`bucket_date`);


-- =============================================================================
-- FOREIGN KEY
-- =============================================================================

-- 인증 / 신청서 / 프로젝트
ALTER TABLE `members`        ADD CONSTRAINT `FK_members_project`           FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `applications`   ADD CONSTRAINT `FK_applications_reviewed_by`  FOREIGN KEY (`reviewed_by`) REFERENCES `members`(`member_id`);
ALTER TABLE `projects`       ADD CONSTRAINT `FK_projects_owner`            FOREIGN KEY (`owner_id`)    REFERENCES `members`(`member_id`);
ALTER TABLE `refresh_tokens` ADD CONSTRAINT `FK_refresh_tokens_member`     FOREIGN KEY (`member_id`)   REFERENCES `members`(`member_id`);
ALTER TABLE `audit_logs`     ADD CONSTRAINT `FK_audit_logs_project`        FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `audit_logs`     ADD CONSTRAINT `FK_audit_logs_member`         FOREIGN KEY (`member_id`) REFERENCES `members`(`member_id`);

-- 고객 / 이벤트
ALTER TABLE `users`               ADD CONSTRAINT `FK_users_project`               FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `devices`             ADD CONSTRAINT `FK_devices_project`             FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `devices`             ADD CONSTRAINT `FK_devices_user`                FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);
ALTER TABLE `events`              ADD CONSTRAINT `FK_events_project`              FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `events`              ADD CONSTRAINT `FK_events_user`                 FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);
ALTER TABLE `events`              ADD CONSTRAINT `FK_events_category`             FOREIGN KEY (`category_id`) REFERENCES `event_categories`(`category_id`);
ALTER TABLE `events`              ADD CONSTRAINT `FK_events_device`               FOREIGN KEY (`device_id`)   REFERENCES `devices`(`device_id`);
ALTER TABLE `event_categories`    ADD CONSTRAINT `FK_event_categories_project`    FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `event_schemas`       ADD CONSTRAINT `FK_event_schemas_project`       FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `event_schemas`       ADD CONSTRAINT `FK_event_schemas_category`      FOREIGN KEY (`category_id`) REFERENCES `event_categories`(`category_id`);
ALTER TABLE `event_schemas`       ADD CONSTRAINT `FK_event_schemas_creator`       FOREIGN KEY (`created_by`)  REFERENCES `members`(`member_id`);
ALTER TABLE `event_schema_fields` ADD CONSTRAINT `FK_event_schema_fields_schema`  FOREIGN KEY (`schema_id`)   REFERENCES `event_schemas`(`schema_id`);
ALTER TABLE `event_daily_agg`     ADD CONSTRAINT `FK_event_daily_agg_project`     FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `event_daily_agg`     ADD CONSTRAINT `FK_event_daily_agg_category`    FOREIGN KEY (`category_id`) REFERENCES `event_categories`(`category_id`);
ALTER TABLE `event_daily_agg`     ADD CONSTRAINT `FK_event_daily_agg_user`        FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);
ALTER TABLE `tag_definitions`     ADD CONSTRAINT `FK_tag_definitions_project`     FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `user_tags`           ADD CONSTRAINT `FK_user_tags_project`           FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `user_tags`           ADD CONSTRAINT `FK_user_tags_user`              FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);

-- 세그먼트
ALTER TABLE `segments`               ADD CONSTRAINT `FK_segments_project`              FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `segments`               ADD CONSTRAINT `FK_segments_creator`              FOREIGN KEY (`created_by`)  REFERENCES `members`(`member_id`);
ALTER TABLE `segment_members`        ADD CONSTRAINT `FK_segment_members_segment`       FOREIGN KEY (`segment_id`)  REFERENCES `segments`(`segment_id`);
ALTER TABLE `segment_members`        ADD CONSTRAINT `FK_segment_members_user`          FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);
ALTER TABLE `segment_favorites`      ADD CONSTRAINT `FK_segment_favorites_segment`     FOREIGN KEY (`segment_id`)  REFERENCES `segments`(`segment_id`);
ALTER TABLE `segment_favorites`      ADD CONSTRAINT `FK_segment_favorites_member`      FOREIGN KEY (`member_id`)   REFERENCES `members`(`member_id`);
ALTER TABLE `segment_snapshots`      ADD CONSTRAINT `FK_segment_snapshots_segment`     FOREIGN KEY (`segment_id`)  REFERENCES `segments`(`segment_id`);
ALTER TABLE `segment_snapshots`      ADD CONSTRAINT `FK_segment_snapshots_campaign`    FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `segment_snapshots`      ADD CONSTRAINT `FK_segment_snapshots_execution`   FOREIGN KEY (`campaign_execution_id`) REFERENCES `campaign_executions`(`campaign_execution_id`);
ALTER TABLE `segment_snapshot_users` ADD CONSTRAINT `FK_segment_snapshot_users_snapshot` FOREIGN KEY (`snapshot_id`) REFERENCES `segment_snapshots`(`snapshot_id`);
ALTER TABLE `segment_snapshot_users` ADD CONSTRAINT `FK_segment_snapshot_users_user`     FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);

-- 캠페인 / 메시지 템플릿
ALTER TABLE `message_templates`   ADD CONSTRAINT `FK_message_templates_project` FOREIGN KEY (`project_id`)          REFERENCES `projects`(`project_id`);
ALTER TABLE `message_templates`   ADD CONSTRAINT `FK_message_templates_creator` FOREIGN KEY (`member_id`)           REFERENCES `members`(`member_id`);
ALTER TABLE `campaigns`           ADD CONSTRAINT `FK_campaigns_project`         FOREIGN KEY (`project_id`)          REFERENCES `projects`(`project_id`);
ALTER TABLE `campaigns`           ADD CONSTRAINT `FK_campaigns_template`        FOREIGN KEY (`message_template_id`) REFERENCES `message_templates`(`message_template_id`);
ALTER TABLE `campaigns`           ADD CONSTRAINT `FK_campaigns_segment`         FOREIGN KEY (`segment_id`)          REFERENCES `segments`(`segment_id`);
ALTER TABLE `campaigns`           ADD CONSTRAINT `FK_campaigns_creator`         FOREIGN KEY (`member_id`)           REFERENCES `members`(`member_id`);
ALTER TABLE `campaign_executions` ADD CONSTRAINT `FK_campaign_executions_campaign` FOREIGN KEY (`campaign_id`)      REFERENCES `campaigns`(`campaign_id`);

-- 발송 / 사용자 행동
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_user`     FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_device`   FOREIGN KEY (`device_id`)   REFERENCES `devices`(`device_id`);
ALTER TABLE `user_action_logs`  ADD CONSTRAINT `FK_user_action_logs_project`   FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `user_action_logs`  ADD CONSTRAINT `FK_user_action_logs_campaign`  FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `user_action_logs`  ADD CONSTRAINT `FK_user_action_logs_log`       FOREIGN KEY (`log_id`)      REFERENCES `notification_logs`(`log_id`);
ALTER TABLE `user_action_logs`  ADD CONSTRAINT `FK_user_action_logs_user`      FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);
ALTER TABLE `user_action_logs`  ADD CONSTRAINT `FK_user_action_logs_device`    FOREIGN KEY (`device_id`)   REFERENCES `devices`(`device_id`);

-- 분석
ALTER TABLE `campaign_stats_daily`  ADD CONSTRAINT `FK_campaign_stats_daily_project`   FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `campaign_stats_daily`  ADD CONSTRAINT `FK_campaign_stats_daily_campaign`  FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `campaign_stats_hourly` ADD CONSTRAINT `FK_campaign_stats_hourly_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `campaign_stats_hourly` ADD CONSTRAINT `FK_campaign_stats_hourly_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `template_stats_daily`  ADD CONSTRAINT `FK_template_stats_daily_project`   FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `template_stats_daily`  ADD CONSTRAINT `FK_template_stats_daily_template`  FOREIGN KEY (`template_id`) REFERENCES `message_templates`(`message_template_id`);
ALTER TABLE `failure_stats_daily`   ADD CONSTRAINT `FK_failure_stats_daily_project`    FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `project_stats_daily`   ADD CONSTRAINT `FK_project_stats_daily_project`    FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `send_reconciliations`  ADD CONSTRAINT `FK_send_reconciliations_project`   FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `send_reconciliations`  ADD CONSTRAINT `FK_send_reconciliations_campaign`  FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);


-- =============================================================================
-- 인덱스 (조회 패턴 / 운영 배치 기반)
-- =============================================================================

-- 인증 / 회원 / 토큰
CREATE INDEX `idx_members_project_role`   ON `members`(`project_id`,`role_type`,`is_deleted`);
CREATE INDEX `idx_refresh_tokens_member`  ON `refresh_tokens`(`member_id`,`expires_at`);
CREATE INDEX `idx_token_blacklist_expires` ON `token_blacklist`(`expires_at`);

-- 신청서 (목록 조회 / 상태 필터)
CREATE INDEX `idx_applications_status_created` ON `applications`(`status`,`created_at`);
CREATE INDEX `idx_applications_email`          ON `applications`(`email`);

-- 감사 로그
CREATE INDEX `idx_audit_logs_member_time` ON `audit_logs`(`member_id`,`created_at`);
CREATE INDEX `idx_audit_logs_project_time` ON `audit_logs`(`project_id`,`created_at`);
CREATE INDEX `idx_audit_logs_target`      ON `audit_logs`(`target_type`,`target_id`);

-- 고객 / 기기
CREATE INDEX `idx_users_project_signup`    ON `users`(`project_id`,`signed_up_at`);
CREATE INDEX `idx_devices_user`            ON `devices`(`user_id`);
CREATE INDEX `idx_devices_project_token`   ON `devices`(`project_id`,`token_valid`);

-- 이벤트 (세그먼트 빌드 / 유저 조회 / 카테고리별 조회)
CREATE INDEX `idx_events_project_cat_time` ON `events`(`project_id`,`category_id`,`occurred_at`);
CREATE INDEX `idx_events_project_user_time` ON `events`(`project_id`,`user_id`,`occurred_at`);
CREATE INDEX `idx_events_project_time`     ON `events`(`project_id`,`occurred_at`);

-- 이벤트 스키마
CREATE INDEX `idx_event_schemas_current`         ON `event_schemas`(`project_id`,`category_id`,`is_current`);
CREATE INDEX `idx_event_schema_fields_schema`    ON `event_schema_fields`(`schema_id`);

-- 태그
CREATE INDEX `idx_user_tags_user`       ON `user_tags`(`user_id`,`tag_key`);
CREATE INDEX `idx_user_tags_key_value`  ON `user_tags`(`project_id`,`tag_key`,`tag_value`);

-- 세그먼트 (목록 조회 / 빌드 상태 / 만료 배치)
CREATE INDEX `idx_segments_project_list`       ON `segments`(`project_id`,`is_deleted`,`updated_at`);
CREATE INDEX `idx_segments_build_status`       ON `segments`(`build_status`);
CREATE INDEX `idx_segment_members_expires`     ON `segment_members`(`expires_at`);
CREATE INDEX `idx_segment_members_user`        ON `segment_members`(`user_id`,`segment_id`);
CREATE INDEX `idx_segment_favorites_member`    ON `segment_favorites`(`member_id`,`created_at`);
CREATE INDEX `idx_segment_snapshots_campaign`  ON `segment_snapshots`(`campaign_id`,`created_at`);
CREATE INDEX `idx_segment_snapshots_segment`   ON `segment_snapshots`(`segment_id`,`created_at`);
CREATE INDEX `idx_segment_snapshots_execution` ON `segment_snapshots`(`campaign_execution_id`);

-- 캠페인 (목록 조회 / 스케줄러 / 세그먼트 변경 영향 조회)
CREATE INDEX `idx_campaigns_project_list`     ON `campaigns`(`project_id`,`is_deleted`,`updated_at`);
CREATE INDEX `idx_campaigns_scheduler`        ON `campaigns`(`campaign_status`,`next_run_at`);
CREATE INDEX `idx_campaigns_segment`          ON `campaigns`(`segment_id`);
CREATE INDEX `idx_campaigns_template`         ON `campaigns`(`message_template_id`);
CREATE INDEX `idx_campaign_executions_status` ON `campaign_executions`(`campaign_execution_status`,`send_started_at`);

-- 메시지 템플릿
CREATE INDEX `idx_message_templates_project_list` ON `message_templates`(`project_id`,`is_deleted`,`updated_at`);

-- 발송 / 사용자 행동
CREATE INDEX `idx_notification_logs_campaign_status` ON `notification_logs`(`campaign_id`,`status`);
CREATE INDEX `idx_notification_logs_user_time`       ON `notification_logs`(`user_id`,`created_at`);
CREATE INDEX `idx_notification_logs_project_time`    ON `notification_logs`(`project_id`,`created_at`);
CREATE INDEX `idx_user_action_logs_campaign_action`  ON `user_action_logs`(`campaign_id`,`action_type`);
CREATE INDEX `idx_user_action_logs_user_time`        ON `user_action_logs`(`user_id`,`action_at`);
CREATE INDEX `idx_user_action_logs_log`              ON `user_action_logs`(`log_id`);

-- 분석 집계 (대시보드 조회 패턴)
CREATE INDEX `idx_campaign_stats_daily_project_date`  ON `campaign_stats_daily`(`project_id`,`bucket_date`);
CREATE INDEX `idx_campaign_stats_hourly_project_hour` ON `campaign_stats_hourly`(`project_id`,`bucket_hour`);
CREATE INDEX `idx_template_stats_daily_project_date`  ON `template_stats_daily`(`project_id`,`bucket_date`);
CREATE INDEX `idx_failure_stats_daily_project_date`   ON `failure_stats_daily`(`project_id`,`bucket_date`);
CREATE INDEX `idx_send_reconciliations_campaign`      ON `send_reconciliations`(`campaign_id`,`checked_at`);
CREATE INDEX `idx_system_health_snapshots_metric_time` ON `system_health_snapshots`(`metric_name`,`collected_at`);
