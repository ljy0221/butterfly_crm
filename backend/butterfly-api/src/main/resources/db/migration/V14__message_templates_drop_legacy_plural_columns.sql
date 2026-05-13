-- V14: message_templates 의 plural 잔재 컬럼 제거 — 도메인 엔티티 매핑(singular)에 정렬.
--
-- 배경:
--   V1__core_baseline.sql 이 plural(message_templates_*) 과 singular(message_template_*)
--   두 세트를 모두 NOT NULL 로 정의했으나, JPA 엔티티
--   MessageTemplate(@Column name = "message_template_*") 는 singular 만 사용한다.
--   결과적으로 신규 INSERT 시 plural 컬럼에 디폴트가 없어 SQLException 발생
--   (S14P31B101-XXX 회귀). 도메인 매핑이 정답이므로 plural 5개를 drop 한다.
--
-- 영향 범위:
--   - 신규 환경: V1 직후 V14 가 즉시 적용되어 plural 컬럼은 존재하지 않은 것과 동일.
--   - 기존 환경: plural 에만 의미 있는 데이터가 적재된 row 는 없다 — JPA 가
--     singular 만 기록하므로 plural 은 항상 INSERT 시점에 채워진 동일 값 또는
--     기록 자체가 막혀온 컬럼이다. 따라서 백필 없이 안전하게 drop 한다.
ALTER TABLE message_templates
    DROP COLUMN message_templates_name,
    DROP COLUMN message_templates_description,
    DROP COLUMN message_templates_title,
    DROP COLUMN message_templates_body,
    DROP COLUMN message_templates_image_url;
