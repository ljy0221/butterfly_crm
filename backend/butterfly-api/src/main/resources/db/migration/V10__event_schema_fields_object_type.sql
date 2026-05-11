-- event_schema_fields.field_type 에 OBJECT 추가
-- frontend PropertyType 는 string/number/boolean/datetime/object 5 종을 사용하나
-- 기존 ENUM 은 STRING/NUMBER/BOOLEAN/DATE 4 종만 정의되어 OBJECT 저장 불가.
-- ALTER TABLE MODIFY 로 enum 값을 확장 — 기존 row 는 영향 없음.
ALTER TABLE `event_schema_fields`
    MODIFY COLUMN `field_type` ENUM('STRING', 'NUMBER', 'BOOLEAN', 'DATE', 'OBJECT')
        NOT NULL DEFAULT 'STRING';
