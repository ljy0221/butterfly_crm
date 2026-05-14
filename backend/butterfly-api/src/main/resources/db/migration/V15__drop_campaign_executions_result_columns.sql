-- V15: campaign_executions 의 결과 통계 3개 컬럼 제거 — 도메인 정합성 정리.
--
-- 배경:
--   V1__core_baseline.sql 이 sent_count / failed_count / fail_reason 를 정의했으나,
--   worker→campaign 결과 통보 흐름이 미구현이라 현재 항상 0 / null placeholder.
--   실제 발송 결과의 단일 진실 원천은 butterfly-notification 모듈의
--   notification_logs + campaign_stats 테이블이며, reporting 측 read-model 도
--   별 트랙(notification-send-result 토픽 producer)으로 채워질 예정이라
--   campaign 도메인의 3개 컬럼은 중복/미사용 자리.
--
-- 보존:
--   target_count 는 유지한다 — CampaignExecutionInfoProjection 과 reporting 의
--   CampaignExecutionInfo.targetCount 가 그대로 참조하므로 호환 유지.
--
-- 영향 범위:
--   - 신규 환경: V1 직후 V15 가 즉시 적용되어 3개 컬럼은 존재하지 않은 것과 동일.
--   - 기존 환경: 모든 row 가 0 / null placeholder 상태이므로 백필 없이 안전하게 drop.
ALTER TABLE campaign_executions
    DROP COLUMN sent_count,
    DROP COLUMN failed_count,
    DROP COLUMN fail_reason;
