-- =============================================================================
-- V13: notification_logs.message_key 컬럼 + UNIQUE INDEX 추가 (S14P31B101-174)
--
-- 목적: Kafka 메시지(`notification-send-result`) 멱등성 보장의 1차 방어선.
-- 같은 messageKey가 두 번 들어오면 UNIQUE INDEX가 중복 INSERT를 차단한다.
--
-- NULL 정책: MySQL UNIQUE INDEX는 NULL을 다중 허용하므로 기존 row(NULL)에는
-- 영향이 없다. 신규/재처리 메시지는 NOT NULL로 들어와 멱등 키로 동작한다.
-- 백필은 별도 운영 도구로 진행하지 않는다 (이전 데이터는 멱등 보장 대상 외).
-- =============================================================================

ALTER TABLE `notification_logs`
    ADD COLUMN `message_key` VARCHAR(128) NULL COMMENT 'Kafka 메시지 멱등성 키 (notification-send-result)';

ALTER TABLE `notification_logs`
    ADD UNIQUE INDEX `uk_notification_logs_message_key` (`message_key`);
