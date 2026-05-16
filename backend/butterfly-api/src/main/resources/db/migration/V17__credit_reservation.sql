-- V17: 크레딧 예약/정산 모델
--
-- 동시 캠페인 발송 시 같은 프로젝트 잔액을 초과 예약하지 않도록
-- credit_account에 예약 잔액을 추가하고, campaign_execution 단위 예약 원장을 둔다.

ALTER TABLE `credit_account`
    ADD COLUMN `reserved_balance` BIGINT NOT NULL DEFAULT 0
        COMMENT '아직 정산되지 않은 캠페인 실행 예약 금액' AFTER `balance`,
    ADD CONSTRAINT `ck_credit_account_reserved_nonneg` CHECK (`reserved_balance` >= 0),
    ADD CONSTRAINT `ck_credit_account_reserved_lte_balance` CHECK (`reserved_balance` <= `balance`);

CREATE TABLE `credit_reservation` (
    `credit_reservation_id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id`            BIGINT NOT NULL,
    `campaign_id`           BIGINT NOT NULL,
    `campaign_execution_id` BIGINT NOT NULL,
    `expected_count`        INT NOT NULL,
    `unit_price`            BIGINT NOT NULL,
    `reserved_amount`       BIGINT NOT NULL,
    `settled_amount`        BIGINT NOT NULL DEFAULT 0,
    `released_amount`       BIGINT NOT NULL DEFAULT 0,
    `status`                VARCHAR(32) NOT NULL,
    `created_at`            DATETIME NULL,
    `updated_at`            DATETIME NULL,
    PRIMARY KEY (`credit_reservation_id`),
    UNIQUE KEY `uk_credit_reservation_execution` (`campaign_execution_id`),
    KEY `idx_credit_reservation_project_status` (`project_id`, `status`, `created_at`),
    CONSTRAINT `ck_credit_reservation_expected_nonneg` CHECK (`expected_count` >= 0),
    CONSTRAINT `ck_credit_reservation_unit_positive` CHECK (`unit_price` > 0),
    CONSTRAINT `ck_credit_reservation_reserved_nonneg` CHECK (`reserved_amount` >= 0),
    CONSTRAINT `ck_credit_reservation_settled_nonneg` CHECK (`settled_amount` >= 0),
    CONSTRAINT `ck_credit_reservation_released_nonneg` CHECK (`released_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `credit_transaction`
    ADD COLUMN `reference_type` VARCHAR(32) NULL
        COMMENT 'SPEND/ADJUST 출처 타입. 예: CAMPAIGN_EXECUTION' AFTER `payment_order_id`,
    ADD COLUMN `reference_id` BIGINT NULL
        COMMENT 'reference_type에 대응하는 외부 식별자' AFTER `reference_type`,
    ADD UNIQUE KEY `uk_credit_transaction_reference_reason`
        (`project_id`, `reason`, `reference_type`, `reference_id`),
    ADD KEY `idx_credit_transaction_reference` (`reference_type`, `reference_id`);
