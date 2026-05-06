-- V8: 세그먼트 mixed EVENT 조건과 event_data 필터 조회용 인덱스
CREATE INDEX `idx_event_daily_agg_segment_lookup`
    ON `event_daily_agg` (`project_id`, `category_id`, `user_id`, `log_date`);

CREATE INDEX `idx_events_segment_filter_lookup`
    ON `events` (`project_id`, `category_id`, `user_id`, `occurred_at`);
