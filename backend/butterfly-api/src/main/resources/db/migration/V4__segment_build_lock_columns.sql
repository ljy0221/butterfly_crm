-- =============================================================================
-- V4: segment 도메인 Phase 6-2 (S14P31B101-178) 빌드 잡 동시성 컬럼 보강 +
--     schema_version 기본값 v3 → v4 (ADR-0015 / S14P31B101-179)
--
-- 출처: develop 엔티티 (Segment.java) ↔ V1 baseline 차이
--   - V1 baseline은 ERD 시점 정의(schema_version DEFAULT 1, build_lock 컬럼 없음)
--   - 이후 Phase 6-2/6-3에서 추가:
--       * build_token        : CAS 안전장치 — 빌드 worker 인스턴스 UUID
--       * build_started_at   : 빌드 시작 시각 (마지막)
--       * build_heartbeat_at : chunk 처리 중 heartbeat — stale 감지용
--   - ADR-0015 (S14P31B101-179): EventNode.category(name) → categoryId(Long) 정규화로
--     condition_tree JSON 스키마 v4. 컬럼 자체는 그대로(JSON), 기본값만 v4로.
-- =============================================================================

ALTER TABLE `segments`
    ADD COLUMN `build_token`        VARCHAR(36) NULL COMMENT 'CAS 안전장치 — 빌드 worker 인스턴스 UUID' AFTER `build_status`,
    ADD COLUMN `build_started_at`   DATETIME(6) NULL COMMENT '빌드 시작 시각 (마지막)'                    AFTER `build_token`,
    ADD COLUMN `build_heartbeat_at` DATETIME(6) NULL COMMENT 'chunk 처리 중 heartbeat — stale 감지'    AFTER `build_started_at`;

ALTER TABLE `segments`
    ALTER COLUMN `schema_version` SET DEFAULT 4;
