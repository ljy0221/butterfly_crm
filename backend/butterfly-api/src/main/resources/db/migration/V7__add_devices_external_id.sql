-- V7: devices 테이블에 external_id 추가
-- ADR-0012: 외부 식별자 기반 디바이스 조회 — SDK가 전달하는 deviceId를 저장.
-- users.external_id와 동일한 패턴. (project_id, external_id) 조합 unique.
ALTER TABLE `devices`
    ADD COLUMN `external_id` VARCHAR(255) NOT NULL DEFAULT '' AFTER `user_id`,
    ADD UNIQUE KEY `uk_devices_project_external` (`project_id`, `external_id`);
