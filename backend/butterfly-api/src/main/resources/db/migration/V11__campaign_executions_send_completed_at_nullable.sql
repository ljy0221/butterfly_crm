-- V11: campaign_executions.send_completed_at NOT NULL → NULL 허용
--
-- V1 baseline 에서 NOT NULL 로 정의됐으나, 엔티티(CampaignExecution)는 nullable 매핑이고
-- ready() 팩토리(READY 상태)에서는 send_completed_at 을 null 로 둔다 — 완료 시각이라
-- 발송 전엔 채울 값이 없는 게 자연스럽다. INSERT 시점에 NOT NULL 위반으로 500 이 나던 결함을
-- 엔티티 정의에 맞춰 정정한다. markAsSent/markAsCompleted 시점에 LocalDateTime.now()로 채워진다.
ALTER TABLE campaign_executions
    MODIFY COLUMN `send_completed_at` DATETIME NULL;
