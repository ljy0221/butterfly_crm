-- 5/22 시연용 — 히스토리 캠페인 6건 + 실행 이력 시드
--
-- V15: campaign_executions 에서 sent_count / failed_count / fail_reason 컬럼 삭제됨
--   → INSERT 시 해당 컬럼 사용 금지. target_count 만 유효.
-- V11: send_completed_at NULL 허용 (완료된 캠페인이므로 값 채움)
-- V12: send_started_at NULL 허용 (완료된 캠페인이므로 값 채움)
-- V6:  campaign_executions.version BIGINT NOT NULL DEFAULT 0 존재
--
-- 대시보드 깊은 그래프(notification_logs, campaign_stats 등)는 이 시드에 포함되지 않음.
-- D-3 리허설에서 필요 시 별도 적재 필요 — README 참조.

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM campaign_executions
WHERE campaign_id IN (
    SELECT campaign_id FROM campaigns WHERE project_id = 1
);
DELETE FROM campaigns WHERE project_id = 1;

-- ──────────────────────────────────────────────────────────────────────────────
-- 캠페인 6건 (ENDED)
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO campaigns (campaign_id, project_id, message_template_id, segment_id, member_id,
                       campaign_name, campaign_description,
                       campaign_type, channel, campaign_status, send_mode,
                       version, is_deleted, created_at, updated_at)
VALUES
(101, 1, 1, NULL, 1,
 '봄 신상 안내', '봄 신상품 출시 알림 푸시',
 'ONE_TIME', 'PUSH', 'ENDED', 'IMMEDIATE',
 0, FALSE, NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 27 DAY),

(102, 1, 1, NULL, 1,
 '재방문 유도', '7일 미방문 고객 이메일 유도',
 'ONE_TIME', 'EMAIL', 'ENDED', 'IMMEDIATE',
 0, FALSE, NOW() - INTERVAL 24 DAY, NOW() - INTERVAL 23 DAY),

(103, 1, 1, NULL, 1,
 'VIP 할인 안내', 'VIP 등급 전용 할인 푸시',
 'ONE_TIME', 'PUSH', 'ENDED', 'IMMEDIATE',
 0, FALSE, NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 18 DAY),

(104, 1, 1, NULL, 1,
 '주말 특가', '주말 한정 특가 알림 푸시',
 'ONE_TIME', 'PUSH', 'ENDED', 'IMMEDIATE',
 0, FALSE, NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 13 DAY),

(105, 1, 1, NULL, 1,
 '쿠폰 만료 알림', '쿠폰 만료 3일전 리마인더 푸시',
 'ONE_TIME', 'PUSH', 'ENDED', 'IMMEDIATE',
 0, FALSE, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 7 DAY),

(106, 1, 1, NULL, 1,
 '리뷰 요청', '구매 후 리뷰 요청 푸시',
 'ONE_TIME', 'PUSH', 'ENDED', 'IMMEDIATE',
 0, FALSE, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY);

-- ──────────────────────────────────────────────────────────────────────────────
-- 실행 이력 — 캠페인당 1건 (SUCCESS, target_count 만 기재)
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO campaign_executions (campaign_id, run_round, campaign_execution_status,
                                 send_started_at, send_completed_at,
                                 target_count, version)
VALUES
(101, 1, 'SUCCESS',
 NOW() - INTERVAL 28 DAY,
 NOW() - INTERVAL 28 DAY + INTERVAL 5 MINUTE,
 2300, 0),

(102, 1, 'SUCCESS',
 NOW() - INTERVAL 24 DAY,
 NOW() - INTERVAL 24 DAY + INTERVAL 4 MINUTE,
 1800, 0),

(103, 1, 'SUCCESS',
 NOW() - INTERVAL 19 DAY,
 NOW() - INTERVAL 19 DAY + INTERVAL 3 MINUTE,
 850, 0),

(104, 1, 'SUCCESS',
 NOW() - INTERVAL 14 DAY,
 NOW() - INTERVAL 14 DAY + INTERVAL 6 MINUTE,
 4200, 0),

(105, 1, 'SUCCESS',
 NOW() - INTERVAL 8 DAY,
 NOW() - INTERVAL 8 DAY + INTERVAL 2 MINUTE,
 1100, 0),

(106, 1, 'SUCCESS',
 NOW() - INTERVAL 3 DAY,
 NOW() - INTERVAL 3 DAY + INTERVAL 4 MINUTE,
 2750, 0);

SET FOREIGN_KEY_CHECKS = 1;
