-- V12: campaign_executions.send_started_at NOT NULL → NULL 허용
--
-- V11 후속 — send_completed_at 만 처리했고 send_started_at 도 같은 결함이 있어 한 번 더 ALTER.
-- 엔티티 의도: READY 상태에서는 발송 시작 전이라 null 이 정상이며, markAsSent() 호출 시점에
-- LocalDateTime.now()로 채워진다. (CampaignActivator.activate 의 publish 직후)
ALTER TABLE campaign_executions
    MODIFY COLUMN `send_started_at` DATETIME NULL;
