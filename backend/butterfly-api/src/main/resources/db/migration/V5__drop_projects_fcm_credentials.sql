-- V5: projects 테이블에서 fcm_credentials 컬럼 제거
-- FCM 자격증명 관리가 notification 모듈로 이관됨
ALTER TABLE projects DROP COLUMN fcm_credentials;
