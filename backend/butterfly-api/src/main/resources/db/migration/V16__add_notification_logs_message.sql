-- S14P31B101 PWA polling: notification_logs에 메시지 내용 저장 컬럼 추가
ALTER TABLE notification_logs
    ADD COLUMN title     VARCHAR(255) NULL AFTER channel,
    ADD COLUMN body      TEXT         NULL AFTER title,
    ADD COLUMN click_url VARCHAR(500) NULL AFTER body;
