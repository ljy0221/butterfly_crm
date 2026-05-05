-- =============================================================================
-- V2: reporting_baseline (A그룹 10개 테이블)
-- 출처: butterfly-reporting/src/main/resources/db/migration/V1~V11 통합 + V13 컬럼 흡수
-- 정의는 reporting 모듈의 정제된 정의를 그대로 사용 (충돌 시 reporting 우선 — spec §4.3)
-- FK는 V2에 없음 (ADR-0013 §5 — 후속 V로 분리)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- notification_logs (출처: reporting V1 + V13 message_key 통합)
-- 발송 1건 = 1 row, 상태 전이는 UPDATE
-- -----------------------------------------------------------------------------

CREATE TABLE `notification_logs` (
    `log_id`                 BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '로그 고유 ID',
    `project_id`             BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`            BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `campaign_execution_id`  BIGINT       NULL                     COMMENT '캠페인 실행 회차 ID (반복 캠페인 회차 추적)',
    `message_template_id`    BIGINT       NULL                     COMMENT '사용된 템플릿 ID',
    `user_id`                BIGINT       NULL                     COMMENT '유저 ID',
    `device_id`              BIGINT       NULL                     COMMENT '기기 ID',
    `channel`                ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '발송 채널',
    `status`                 ENUM('PENDING','SENT','DELIVERED','FAILED') NOT NULL COMMENT '발송 상태 (CLICKED는 user_action_logs로 분리)',
    `error_code`             VARCHAR(50)  NULL                     COMMENT '에러 코드',
    `error_message`          TEXT         NULL                     COMMENT '에러 메시지',
    `sent_at`                DATETIME     NULL                     COMMENT '발송 시각',
    `delivered_at`           DATETIME     NULL                     COMMENT '도달 시각',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '로그 생성 시각',
    `message_key`            VARCHAR(128) NULL                     COMMENT 'Kafka 메시지 멱등성 키 (notification-send-result)',

    PRIMARY KEY (`log_id`),
    UNIQUE KEY `uk_notification_logs_message_key`           (`message_key`),
    KEY `idx_notification_logs_project_time`    (`project_id`, `created_at`),
    KEY `idx_notification_logs_campaign_status` (`campaign_id`, `status`),
    KEY `idx_notification_logs_execution`       (`campaign_execution_id`),
    KEY `idx_notification_logs_template`        (`message_template_id`),
    KEY `idx_notification_logs_user_time`       (`user_id`, `created_at`),
    KEY `idx_notification_logs_channel_status`  (`channel`, `status`),
    KEY `idx_notification_logs_error`           (`error_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='알림 발송 로그 (발송 1건 = 1 row, 상태 전이는 UPDATE)';

-- -----------------------------------------------------------------------------
-- user_action_logs (출처: reporting V2)
-- -----------------------------------------------------------------------------

CREATE TABLE `user_action_logs` (
    `action_id`      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '액션 고유 ID',
    `log_id`         BIGINT       NOT NULL                 COMMENT 'notification_logs.log_id',
    `project_id`     BIGINT       NOT NULL                 COMMENT '조회 성능용 비정규화',
    `campaign_id`    BIGINT       NOT NULL                 COMMENT '조회 성능용 비정규화',
    `user_id`        BIGINT       NULL                     COMMENT '유저 ID',
    `device_id`      BIGINT       NULL                     COMMENT '기기 ID (이메일/웹 클릭은 NULL)',
    `action_type`    ENUM('OPEN','CLICK','CONVERT','DISMISS') NOT NULL COMMENT '행동 종류',
    `action_target`  VARCHAR(500) NULL                     COMMENT '클릭한 링크 URL 등',
    `user_agent`     VARCHAR(500) NULL                     COMMENT 'User-Agent',
    `action_at`      DATETIME     NOT NULL                 COMMENT '행동 발생 시각',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '로그 생성 시각',

    PRIMARY KEY (`action_id`),
    KEY `idx_user_action_logs_log`             (`log_id`),
    KEY `idx_user_action_logs_campaign_action` (`campaign_id`, `action_type`, `action_at`),
    KEY `idx_user_action_logs_user_time`       (`user_id`, `action_at`),
    KEY `idx_user_action_logs_project_time`    (`project_id`, `action_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='사용자 반응 로그 (OPEN/CLICK/CONVERT/DISMISS)';

-- -----------------------------------------------------------------------------
-- audit_logs (출처: reporting V3) — append-only, 트리거는 V3 마이그레이션에서 생성
-- -----------------------------------------------------------------------------

CREATE TABLE `audit_logs` (
    `audit_id`     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '감사 로그 ID',
    `project_id`   BIGINT       NULL                     COMMENT '프로젝트 ID (시스템 레벨 시 NULL)',
    `member_id`    BIGINT       NOT NULL                 COMMENT '작업 수행 회원 ID',
    `action`       VARCHAR(100) NOT NULL                 COMMENT '작업 종류 (FCM_KEY_UPDATE, API_KEY_REISSUE 등)',
    `target_type`  VARCHAR(50)  NOT NULL                 COMMENT '대상 엔티티 타입',
    `target_id`    BIGINT       NULL                     COMMENT '대상 엔티티 ID',
    `detail`       JSON         NULL                     COMMENT '추가 상세 (변경 전/후 값 등)',
    `ip_address`   VARCHAR(45)  NULL                     COMMENT '요청 IP (IPv6 대응)',
    `user_agent`   VARCHAR(500) NULL                     COMMENT 'User-Agent',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작업 시각',

    PRIMARY KEY (`audit_id`),
    KEY `idx_audit_logs_project_action` (`project_id`, `action`, `created_at`),
    KEY `idx_audit_logs_actor`          (`member_id`, `created_at`),
    KEY `idx_audit_logs_target`         (`target_type`, `target_id`),
    KEY `idx_audit_logs_action_time`    (`action`, `created_at`),
    KEY `idx_audit_logs_created_at`     (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='감사 로그 (append-only: INSERT/SELECT만 허용)';

-- -----------------------------------------------------------------------------
-- send_reconciliations (출처: reporting V5)
-- -----------------------------------------------------------------------------

CREATE TABLE `send_reconciliations` (
    `recon_id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '검증 ID',
    `project_id`             BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`            BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `campaign_execution_id`  BIGINT       NULL                     COMMENT '캠페인 실행 회차 ID',
    `channel`                ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `expected_count`         INT          NOT NULL                 COMMENT '세그먼트 스냅샷 대상자 수',
    `actual_count`           INT          NOT NULL                 COMMENT '실제 notification_logs 기록 수',
    `sent_count`             INT          NOT NULL DEFAULT 0       COMMENT 'SENT 상태 수',
    `failed_count`           INT          NOT NULL DEFAULT 0       COMMENT 'FAILED 상태 수',
    `diff_count`             INT          NOT NULL                 COMMENT 'expected - actual',
    `is_consistent`          BOOLEAN      NOT NULL                 COMMENT 'diff == 0',
    `note`                   VARCHAR(500) NULL                     COMMENT '비고',
    `checked_at`             DATETIME     NOT NULL                 COMMENT '검증 수행 시각',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',

    PRIMARY KEY (`recon_id`),
    KEY `idx_send_reconciliations_campaign`     (`campaign_id`, `checked_at`),
    KEY `idx_send_reconciliations_execution`    (`campaign_execution_id`),
    KEY `idx_send_reconciliations_inconsistent` (`is_consistent`, `checked_at`),
    KEY `idx_send_reconciliations_project_time` (`project_id`, `checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='발송 정합성 검증 결과 (캠페인 회차 × 채널 단위)';

-- -----------------------------------------------------------------------------
-- system_health_snapshots (출처: reporting V6)
-- -----------------------------------------------------------------------------

CREATE TABLE `system_health_snapshots` (
    `snapshot_id`     BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '스냅샷 ID',
    `metric_name`     VARCHAR(50)   NOT NULL                 COMMENT 'kafka_lag, worker_tps, api_error_rate 등',
    `metric_target`   VARCHAR(100)  NULL                     COMMENT 'topic명, worker명, API path',
    `metric_value`    DECIMAL(18,4) NOT NULL                 COMMENT '측정값',
    `threshold_warn`  DECIMAL(18,4) NULL                     COMMENT '경고 임계치',
    `threshold_crit`  DECIMAL(18,4) NULL                     COMMENT '위험 임계치',
    `status`          ENUM('OK','WARN','CRITICAL') NOT NULL  COMMENT '상태',
    `collected_at`    DATETIME      NOT NULL                 COMMENT '수집 시각',

    PRIMARY KEY (`snapshot_id`),
    KEY `idx_system_health_snapshots_metric_time` (`metric_name`, `collected_at`),
    KEY `idx_system_health_snapshots_status_time` (`status`, `collected_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='시스템 건강 상태 스냅샷';

-- -----------------------------------------------------------------------------
-- campaign_stats_hourly (출처: reporting V7)
-- -----------------------------------------------------------------------------

CREATE TABLE `campaign_stats_hourly` (
    `stat_id`          BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`       BIGINT      NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`      BIGINT      NOT NULL                 COMMENT '캠페인 ID',
    `channel`          ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `bucket_hour`      DATETIME    NOT NULL                 COMMENT '시간 버킷 (예: 2026-04-28 15:00:00)',
    `pending_count`    INT         NOT NULL DEFAULT 0       COMMENT '대기 수',
    `sent_count`       INT         NOT NULL DEFAULT 0       COMMENT '발송 수',
    `delivered_count`  INT         NOT NULL DEFAULT 0       COMMENT '도달 수',
    `failed_count`     INT         NOT NULL DEFAULT 0       COMMENT '실패 수',
    `open_count`       INT         NOT NULL DEFAULT 0       COMMENT 'user_action_logs OPEN',
    `click_count`      INT         NOT NULL DEFAULT 0       COMMENT 'user_action_logs CLICK',
    `convert_count`    INT         NOT NULL DEFAULT 0       COMMENT 'user_action_logs CONVERT',
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_campaign_stats_hourly_nat`        (`campaign_id`, `channel`, `bucket_hour`),
    KEY `idx_campaign_stats_hourly_project_hour`     (`project_id`, `bucket_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='캠페인 시간 단위 집계 (실시간 대시보드용)';

-- -----------------------------------------------------------------------------
-- campaign_stats_daily (출처: reporting V8)
-- -----------------------------------------------------------------------------

CREATE TABLE `campaign_stats_daily` (
    `stat_id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`       BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `campaign_id`      BIGINT       NOT NULL                 COMMENT '캠페인 ID',
    `channel`          ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `bucket_date`      DATE         NOT NULL                 COMMENT '일 버킷',
    `sent_count`       INT          NOT NULL DEFAULT 0       COMMENT '발송 수',
    `delivered_count`  INT          NOT NULL DEFAULT 0       COMMENT '도달 수',
    `failed_count`     INT          NOT NULL DEFAULT 0       COMMENT '실패 수',
    `open_count`       INT          NOT NULL DEFAULT 0       COMMENT '오픈 수',
    `click_count`      INT          NOT NULL DEFAULT 0       COMMENT '클릭 수',
    `convert_count`    INT          NOT NULL DEFAULT 0       COMMENT '전환 수',
    `delivery_rate`    DECIMAL(5,2) NULL                     COMMENT 'delivered / sent * 100',
    `open_rate`        DECIMAL(5,2) NULL                     COMMENT 'open / delivered * 100',
    `click_rate`       DECIMAL(5,2) NULL                     COMMENT 'click / open * 100',
    `convert_rate`     DECIMAL(5,2) NULL                     COMMENT 'convert / sent * 100',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_campaign_stats_daily_nat`        (`campaign_id`, `channel`, `bucket_date`),
    KEY `idx_campaign_stats_daily_project_date`     (`project_id`, `bucket_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='캠페인 일 단위 집계 (성과 리포트 API용)';

-- -----------------------------------------------------------------------------
-- template_stats_daily (출처: reporting V9)
-- -----------------------------------------------------------------------------

CREATE TABLE `template_stats_daily` (
    `stat_id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`           BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `message_template_id`  BIGINT       NOT NULL                 COMMENT '메시지 템플릿 ID',
    `channel`              ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `bucket_date`          DATE         NOT NULL                 COMMENT '일 버킷',
    `used_campaign_count`  INT          NOT NULL DEFAULT 0       COMMENT '이 템플릿을 쓴 캠페인 수',
    `sent_count`           INT          NOT NULL DEFAULT 0       COMMENT '발송 수',
    `delivered_count`      INT          NOT NULL DEFAULT 0       COMMENT '도달 수',
    `open_count`           INT          NOT NULL DEFAULT 0       COMMENT '오픈 수',
    `click_count`          INT          NOT NULL DEFAULT 0       COMMENT '클릭 수',
    `convert_count`        INT          NOT NULL DEFAULT 0       COMMENT '전환 수',
    `open_rate`            DECIMAL(5,2) NULL                     COMMENT '오픈률 (%)',
    `click_rate`           DECIMAL(5,2) NULL                     COMMENT '클릭률 (%)',
    `convert_rate`         DECIMAL(5,2) NULL                     COMMENT '전환률 (%)',
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_template_stats_daily_nat`        (`message_template_id`, `channel`, `bucket_date`),
    KEY `idx_template_stats_daily_project_date`     (`project_id`, `bucket_date`),
    KEY `idx_template_stats_daily_top`              (`project_id`, `bucket_date`, `open_rate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='템플릿 일 단위 효율 분석';

-- -----------------------------------------------------------------------------
-- failure_stats_daily (출처: reporting V10)
-- -----------------------------------------------------------------------------

CREATE TABLE `failure_stats_daily` (
    `stat_id`                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`             BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `channel`                ENUM('PUSH','EMAIL','SMS','WEBHOOK','KAKAO') NOT NULL COMMENT '채널',
    `error_code`             VARCHAR(50)  NOT NULL                 COMMENT 'INVALID_TOKEN, AUTH_ERROR 등',
    `bucket_date`            DATE         NOT NULL                 COMMENT '일 버킷',
    `failed_count`           INT          NOT NULL DEFAULT 0       COMMENT '실패 건수',
    `affected_campaign_cnt`  INT          NOT NULL DEFAULT 0       COMMENT '영향 받은 캠페인 수',
    `affected_user_cnt`      INT          NOT NULL DEFAULT 0       COMMENT '영향 받은 유저 수',
    `first_occurred_at`      DATETIME     NULL                     COMMENT '해당일 최초 발생 시각',
    `last_occurred_at`       DATETIME     NULL                     COMMENT '해당일 마지막 발생 시각',
    `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_failure_stats_daily_nat`         (`project_id`, `channel`, `error_code`, `bucket_date`),
    KEY `idx_failure_stats_daily_project_date`      (`project_id`, `bucket_date`),
    KEY `idx_failure_stats_daily_top`               (`project_id`, `bucket_date`, `failed_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='실패 원인 일 단위 집계';

-- -----------------------------------------------------------------------------
-- project_stats_daily (출처: reporting V11)
-- -----------------------------------------------------------------------------

CREATE TABLE `project_stats_daily` (
    `stat_id`                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '통계 ID',
    `project_id`             BIGINT       NOT NULL                 COMMENT '프로젝트 ID',
    `bucket_date`            DATE         NOT NULL                 COMMENT '일 버킷',
    `active_user_count`      INT          NOT NULL DEFAULT 0       COMMENT '당일 이벤트 발생 유저 수',
    `new_user_count`         INT          NOT NULL DEFAULT 0       COMMENT '신규 가입 유저 수',
    `churn_user_count`       INT          NOT NULL DEFAULT 0       COMMENT '이탈 세그먼트 매칭 수',
    `total_sent`             INT          NOT NULL DEFAULT 0       COMMENT '총 발송 수',
    `total_delivered`        INT          NOT NULL DEFAULT 0       COMMENT '총 도달 수',
    `total_failed`           INT          NOT NULL DEFAULT 0       COMMENT '총 실패 수',
    `total_open`             INT          NOT NULL DEFAULT 0       COMMENT '총 오픈 수',
    `total_click`            INT          NOT NULL DEFAULT 0       COMMENT '총 클릭 수',
    `total_convert`          INT          NOT NULL DEFAULT 0       COMMENT '총 전환 수',
    `push_open_rate`         DECIMAL(5,2) NULL                     COMMENT '앱 푸시 오픈률 (%)',
    `overall_click_rate`     DECIMAL(5,2) NULL                     COMMENT '전체 클릭률 (%)',
    `overall_convert_rate`   DECIMAL(5,2) NULL                     COMMENT '전체 전환률 (%)',
    `active_campaign_count`  INT          NOT NULL DEFAULT 0       COMMENT '활성 캠페인 수',
    `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (`stat_id`),
    UNIQUE KEY `uk_project_stats_daily_nat` (`project_id`, `bucket_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='프로젝트 일 단위 집계 (대시보드용)';
