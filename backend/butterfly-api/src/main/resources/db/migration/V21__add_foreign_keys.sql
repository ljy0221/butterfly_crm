-- =============================================================================
-- V21: 전 도메인 FK 제약조건 일괄 추가 (ADR-0013 §5 후조치)
--
-- 적용 전 사전 검증 쿼리(docs/fk-constraint-work-plan.md §4) 실행 필수.
-- 코드 변경 없음 — 기존 비즈니스 로직이 이미 올바른 삽입/삭제 순서를 따름.
--
-- 적용 순서 원칙:
--   1. 순환 참조(projects↔members)는 NULL 허용 방향(members.project_id) 먼저
--   2. 참조되는 부모 테이블 FK가 먼저 추가된 뒤 자식 테이블 FK 추가
--   3. 소프트 딜리트 패턴 전제 — ON DELETE RESTRICT가 대부분 안전
--   4. 로그성·감사 테이블(audit_logs)은 append-only 트리거와 충돌 가능성으로 제외
-- =============================================================================

-- ============================================================
-- 1. Tenant 도메인 — 순환 참조 처리 (NULL 허용 방향 먼저)
-- ============================================================

-- 1-a. members.project_id → projects (NULL 허용이므로 먼저)
ALTER TABLE `members`
    ADD CONSTRAINT `fk_members_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE SET NULL ON UPDATE RESTRICT;

-- 1-b. projects.owner_id → members (NOT NULL — 나중에)
ALTER TABLE `projects`
    ADD CONSTRAINT `fk_projects_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `members` (`member_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 1-c. applications.reviewed_by → members (NULL 허용)
ALTER TABLE `applications`
    ADD CONSTRAINT `fk_applications_reviewer`
        FOREIGN KEY (`reviewed_by`) REFERENCES `members` (`member_id`)
        ON DELETE SET NULL ON UPDATE RESTRICT;

-- ============================================================
-- 2. Auth 도메인
-- ============================================================

-- refresh_tokens: 회원 종속 데이터 — CASCADE로 자동 정리
ALTER TABLE `refresh_tokens`
    ADD CONSTRAINT `fk_refresh_tokens_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`member_id`)
        ON DELETE CASCADE ON UPDATE RESTRICT;

-- ============================================================
-- 3. Event 도메인
-- ============================================================

ALTER TABLE `users`
    ADD CONSTRAINT `fk_users_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `devices`
    ADD CONSTRAINT `fk_devices_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_devices_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `event_categories`
    ADD CONSTRAINT `fk_event_categories_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `event_schemas`
    ADD CONSTRAINT `fk_event_schemas_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_event_schemas_category`
        FOREIGN KEY (`category_id`) REFERENCES `event_categories` (`category_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `event_schema_fields`
    ADD CONSTRAINT `fk_event_schema_fields_schema`
        FOREIGN KEY (`schema_id`) REFERENCES `event_schemas` (`schema_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- event_daily_agg: 복합 PK라 FK 컬럼별 인덱스를 MySQL이 자동 생성
ALTER TABLE `event_daily_agg`
    ADD CONSTRAINT `fk_event_daily_agg_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_event_daily_agg_category`
        FOREIGN KEY (`category_id`) REFERENCES `event_categories` (`category_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_event_daily_agg_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `user_tags`
    ADD CONSTRAINT `fk_user_tags_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_user_tags_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `tag_definitions`
    ADD CONSTRAINT `fk_tag_definitions_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- events: 고용량 테이블 — 기존 인덱스(idx_events_*) 활용으로 FK 오버헤드 최소
ALTER TABLE `events`
    ADD CONSTRAINT `fk_events_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_events_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_events_category`
        FOREIGN KEY (`category_id`) REFERENCES `event_categories` (`category_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_events_device`
        FOREIGN KEY (`device_id`) REFERENCES `devices` (`device_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ============================================================
-- 4. Campaign 도메인
-- ============================================================

ALTER TABLE `message_templates`
    ADD CONSTRAINT `fk_message_templates_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_message_templates_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`member_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- segments보다 먼저: campaigns.segment_id는 NULL 허용(전체 대상 캠페인)
-- segments는 아래 §5에서 정의되지만 MySQL InnoDB는 ADD CONSTRAINT 시점에 테이블 존재만 확인
ALTER TABLE `campaigns`
    ADD CONSTRAINT `fk_campaigns_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_campaigns_template`
        FOREIGN KEY (`message_template_id`) REFERENCES `message_templates` (`message_template_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_campaigns_segment`
        FOREIGN KEY (`segment_id`) REFERENCES `segments` (`segment_id`)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_campaigns_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`member_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `campaign_executions`
    ADD CONSTRAINT `fk_campaign_executions_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`campaign_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ============================================================
-- 5. Segment 도메인
-- ============================================================

ALTER TABLE `segments`
    ADD CONSTRAINT `fk_segments_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_segments_creator`
        FOREIGN KEY (`created_by`) REFERENCES `members` (`member_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `segment_members`
    ADD CONSTRAINT `fk_segment_members_segment`
        FOREIGN KEY (`segment_id`) REFERENCES `segments` (`segment_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_segment_members_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `segment_favorites`
    ADD CONSTRAINT `fk_segment_favorites_segment`
        FOREIGN KEY (`segment_id`) REFERENCES `segments` (`segment_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_segment_favorites_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`member_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `segment_snapshots`
    ADD CONSTRAINT `fk_segment_snapshots_segment`
        FOREIGN KEY (`segment_id`) REFERENCES `segments` (`segment_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_segment_snapshots_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`campaign_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `segment_snapshot_users`
    ADD CONSTRAINT `fk_segment_snapshot_users_snapshot`
        FOREIGN KEY (`snapshot_id`) REFERENCES `segment_snapshots` (`snapshot_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_segment_snapshot_users_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `segment_variable_stats`
    ADD CONSTRAINT `fk_segment_variable_stats_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_segment_variable_stats_segment`
        FOREIGN KEY (`segment_id`) REFERENCES `segments` (`segment_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ============================================================
-- 6. Notification 도메인
-- ============================================================

-- channel_credentials: JPA ddl-auto 생성 테이블 — FK만 Flyway로 추가
ALTER TABLE `channel_credentials`
    ADD CONSTRAINT `fk_channel_credentials_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ============================================================
-- 7. Reporting 도메인
-- ============================================================

-- notification_logs: 고용량. 발송 로그 보존을 위해 nullable 컬럼은 SET NULL
ALTER TABLE `notification_logs`
    ADD CONSTRAINT `fk_notification_logs_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_notification_logs_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`campaign_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_notification_logs_execution`
        FOREIGN KEY (`campaign_execution_id`) REFERENCES `campaign_executions` (`campaign_execution_id`)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_notification_logs_template`
        FOREIGN KEY (`message_template_id`) REFERENCES `message_templates` (`message_template_id`)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_notification_logs_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_notification_logs_device`
        FOREIGN KEY (`device_id`) REFERENCES `devices` (`device_id`)
        ON DELETE SET NULL ON UPDATE RESTRICT;

ALTER TABLE `user_action_logs`
    ADD CONSTRAINT `fk_user_action_logs_log`
        FOREIGN KEY (`log_id`) REFERENCES `notification_logs` (`log_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_user_action_logs_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_user_action_logs_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`campaign_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- audit_logs: ADR-0008 append-only 3중 방어 (V3 트리거와 ON DELETE 충돌 방지) — FK 제외

ALTER TABLE `send_reconciliations`
    ADD CONSTRAINT `fk_send_reconciliations_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_send_reconciliations_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`campaign_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_send_reconciliations_execution`
        FOREIGN KEY (`campaign_execution_id`) REFERENCES `campaign_executions` (`campaign_execution_id`)
        ON DELETE SET NULL ON UPDATE RESTRICT;

ALTER TABLE `campaign_stats_hourly`
    ADD CONSTRAINT `fk_campaign_stats_hourly_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_campaign_stats_hourly_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`campaign_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `campaign_stats_daily`
    ADD CONSTRAINT `fk_campaign_stats_daily_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_campaign_stats_daily_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`campaign_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `template_stats_daily`
    ADD CONSTRAINT `fk_template_stats_daily_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_template_stats_daily_template`
        FOREIGN KEY (`message_template_id`) REFERENCES `message_templates` (`message_template_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `failure_stats_daily`
    ADD CONSTRAINT `fk_failure_stats_daily_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `project_stats_daily`
    ADD CONSTRAINT `fk_project_stats_daily_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ============================================================
-- 8. Payment 도메인
-- ============================================================

ALTER TABLE `payment_order`
    ADD CONSTRAINT `fk_payment_order_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_payment_order_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`member_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `credit_account`
    ADD CONSTRAINT `fk_credit_account_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `credit_transaction`
    ADD CONSTRAINT `fk_credit_transaction_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_credit_transaction_order`
        FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`payment_order_id`)
        ON DELETE SET NULL ON UPDATE RESTRICT;

ALTER TABLE `credit_reservation`
    ADD CONSTRAINT `fk_credit_reservation_project`
        FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_credit_reservation_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`campaign_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `fk_credit_reservation_execution`
        FOREIGN KEY (`campaign_execution_id`) REFERENCES `campaign_executions` (`campaign_execution_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;
