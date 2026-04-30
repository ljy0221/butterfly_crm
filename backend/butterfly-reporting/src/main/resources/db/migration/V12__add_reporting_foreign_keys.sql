-- =============================================================================
-- V12: reporting 모듈 테이블의 FK 일괄 추가 (ADR-0013 §5)
-- 외부 팀 테이블(projects/campaigns/users/devices/members/message_templates/
-- campaign_executions)이 모두 마이그레이션으로 생성된 후에 적용 가능.
-- 본 V12를 적용하기 전 단계에서도 reporting 테이블 자체는 동작 (FK 없이).
--
-- 참고: 외부 팀이 아직 Flyway 도입 전이라면, 다른 모듈은 ddl-auto=update가
-- 만든 테이블이 존재한다 — 그 테이블을 참조해도 동작한다.
-- =============================================================================

-- 발송 / 사용자 행동
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_project`   FOREIGN KEY (`project_id`)            REFERENCES `projects`(`project_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_campaign`  FOREIGN KEY (`campaign_id`)           REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_execution` FOREIGN KEY (`campaign_execution_id`) REFERENCES `campaign_executions`(`campaign_execution_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_template`  FOREIGN KEY (`message_template_id`)   REFERENCES `message_templates`(`message_template_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_user`      FOREIGN KEY (`user_id`)               REFERENCES `users`(`user_id`);
ALTER TABLE `notification_logs` ADD CONSTRAINT `FK_notification_logs_device`    FOREIGN KEY (`device_id`)             REFERENCES `devices`(`device_id`);

ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_log`      FOREIGN KEY (`log_id`)      REFERENCES `notification_logs`(`log_id`) ON DELETE RESTRICT;
ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_user`     FOREIGN KEY (`user_id`)     REFERENCES `users`(`user_id`);
ALTER TABLE `user_action_logs` ADD CONSTRAINT `FK_user_action_logs_device`   FOREIGN KEY (`device_id`)   REFERENCES `devices`(`device_id`);

-- 감사
ALTER TABLE `audit_logs` ADD CONSTRAINT `FK_audit_logs_project` FOREIGN KEY (`project_id`) REFERENCES `projects`(`project_id`);
ALTER TABLE `audit_logs` ADD CONSTRAINT `FK_audit_logs_member`  FOREIGN KEY (`member_id`)  REFERENCES `members`(`member_id`);

-- 정합성
ALTER TABLE `send_reconciliations` ADD CONSTRAINT `FK_send_reconciliations_project`   FOREIGN KEY (`project_id`)            REFERENCES `projects`(`project_id`);
ALTER TABLE `send_reconciliations` ADD CONSTRAINT `FK_send_reconciliations_campaign`  FOREIGN KEY (`campaign_id`)           REFERENCES `campaigns`(`campaign_id`);
ALTER TABLE `send_reconciliations` ADD CONSTRAINT `FK_send_reconciliations_execution` FOREIGN KEY (`campaign_execution_id`) REFERENCES `campaign_executions`(`campaign_execution_id`);

-- 집계
ALTER TABLE `campaign_stats_hourly` ADD CONSTRAINT `FK_campaign_stats_hourly_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `campaign_stats_hourly` ADD CONSTRAINT `FK_campaign_stats_hourly_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);

ALTER TABLE `campaign_stats_daily` ADD CONSTRAINT `FK_campaign_stats_daily_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects`(`project_id`);
ALTER TABLE `campaign_stats_daily` ADD CONSTRAINT `FK_campaign_stats_daily_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns`(`campaign_id`);

ALTER TABLE `template_stats_daily` ADD CONSTRAINT `FK_template_stats_daily_project`  FOREIGN KEY (`project_id`)          REFERENCES `projects`(`project_id`);
ALTER TABLE `template_stats_daily` ADD CONSTRAINT `FK_template_stats_daily_template` FOREIGN KEY (`message_template_id`) REFERENCES `message_templates`(`message_template_id`);

ALTER TABLE `failure_stats_daily` ADD CONSTRAINT `FK_failure_stats_daily_project` FOREIGN KEY (`project_id`) REFERENCES `projects`(`project_id`);

ALTER TABLE `project_stats_daily` ADD CONSTRAINT `FK_project_stats_daily_project` FOREIGN KEY (`project_id`) REFERENCES `projects`(`project_id`);
