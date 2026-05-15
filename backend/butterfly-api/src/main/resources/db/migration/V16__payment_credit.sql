-- V16: 결제 도메인 (ADR-0019) — Toss Payments 기반 프로젝트 단위 크레딧 충전
--
-- 테이블:
--   payment_order        : 결제 주문/거래 (PENDING → CONFIRMED/FAILED/CANCELLED/PARTIAL_CANCELLED)
--   credit_account       : 프로젝트별 현재 잔액 (1 프로젝트 = 1 행)
--   credit_transaction   : 잔액 변동 원장 (append-only)
--
-- 환불(`cancel_amount`, `cancelled_at`)·가상계좌 컬럼은 미리 정의해 PR2 호환 보장.

CREATE TABLE `payment_order` (
    `payment_order_id`     BIGINT NOT NULL AUTO_INCREMENT,
    `project_id`           BIGINT NOT NULL,
    `member_id`            BIGINT NOT NULL                    COMMENT '결제 actor (감사용, 잔액 소유자 아님)',
    `order_id`             VARCHAR(64) NOT NULL               COMMENT '서버 발급 주문 식별자 (Toss orderId)',
    `amount`               BIGINT NOT NULL                    COMMENT '결제 금액 (KRW)',
    `method`               VARCHAR(32) NULL                   COMMENT 'Toss 결제수단 (CARD/EASY_PAY 등)',
    `status`               VARCHAR(32) NOT NULL DEFAULT 'PENDING'
                                                              COMMENT 'PENDING/CONFIRMED/FAILED/CANCELLED/PARTIAL_CANCELLED',
    `payment_key`          VARCHAR(200) NULL                  COMMENT 'Toss paymentKey (confirm 성공 시 기록)',
    `approved_at`          DATETIME NULL                      COMMENT 'Toss 승인 시각',
    `failure_code`         VARCHAR(64) NULL,
    `failure_message`      VARCHAR(500) NULL,
    `cancel_amount`        BIGINT NOT NULL DEFAULT 0          COMMENT '누적 취소 금액 (부분취소 합산)',
    `cancelled_at`         DATETIME NULL,
    `raw_response`         JSON NULL                          COMMENT 'Toss 응답 원본 (디버깅·감사)',
    `created_at`           DATETIME NULL,
    `updated_at`           DATETIME NULL,
    PRIMARY KEY (`payment_order_id`),
    UNIQUE KEY `uk_payment_order_order_id` (`order_id`),
    UNIQUE KEY `uk_payment_order_payment_key` (`payment_key`),
    KEY `idx_payment_order_project_status` (`project_id`, `status`, `created_at`),
    KEY `idx_payment_order_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `credit_account` (
    `project_id`           BIGINT NOT NULL                    COMMENT '잔액 소유 프로젝트 (1:1)',
    `balance`              BIGINT NOT NULL DEFAULT 0,
    `version`              BIGINT NOT NULL DEFAULT 0          COMMENT '낙관 락(향후 전환용) — 1차 PR 은 비관 락 사용',
    `created_at`           DATETIME NULL,
    `updated_at`           DATETIME NULL,
    PRIMARY KEY (`project_id`),
    CONSTRAINT `ck_credit_account_balance_nonneg` CHECK (`balance` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `credit_transaction` (
    `credit_transaction_id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id`            BIGINT NOT NULL,
    `payment_order_id`      BIGINT NULL                       COMMENT 'CHARGE/REFUND 의 원천 주문 (SPEND/ADJUST 는 NULL 가능)',
    `delta`                 BIGINT NOT NULL                   COMMENT '잔액 변동량 (양수=적립, 음수=차감)',
    `reason`                VARCHAR(32) NOT NULL              COMMENT 'CHARGE/REFUND/SPEND/ADJUST',
    `balance_after`         BIGINT NOT NULL                   COMMENT '적용 후 잔액 (검증·감사용)',
    `memo`                  VARCHAR(500) NULL,
    `created_at`            DATETIME NULL,
    `updated_at`            DATETIME NULL,
    PRIMARY KEY (`credit_transaction_id`),
    UNIQUE KEY `uk_credit_transaction_order_reason` (`payment_order_id`, `reason`)
        COMMENT '같은 주문에 대해 CHARGE/REFUND 가 한 번씩만 적립되도록 보장',
    KEY `idx_credit_transaction_project_created` (`project_id`, `created_at`),
    CONSTRAINT `ck_credit_transaction_delta_nonzero` CHECK (`delta` <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
