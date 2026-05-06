-- V6: campaign_executions 테이블에 JPA optimistic locking용 version 컬럼 추가
-- 워커 fan-out에서 같은 executionId 동시 처리 race 방지 — 두 번째 트랜잭션 UPDATE는 OptimisticLockException
ALTER TABLE campaign_executions
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA optimistic locking';
