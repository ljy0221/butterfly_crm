-- ClickHouse CDC 설정 (S14P31B101-275)
--
-- events.collected Kafka 토픽 → ClickHouse events MergeTree 자동 적재.
-- butterfly-event 의 EventProducer 가 모든 신규 event 를 events.collected 에 publish 하므로,
-- ClickHouse 의 Kafka Engine 이 자체 consumer 로 받아 MaterializedView 를 통해 MergeTree 에 INSERT.
--
-- 실행:
--   docker exec -i butterfly-clickhouse clickhouse-client \
--     --user butterfly --password butterfly --database butterfly \
--     < scripts/clickhouse-cdc-setup.sql
--
-- 멱등성: CREATE ... IF NOT EXISTS 라 여러 번 실행해도 안전.

-- 1) Kafka engine queue table — events.collected 토픽을 직접 read.
--    EventPayload (record) 의 JSON 필드와 동일한 컬럼 (camelCase 매핑 SETTING).
CREATE TABLE IF NOT EXISTS butterfly.events_kafka_queue
(
    event_uuid     String,
    project_id     UInt64,
    user_id        UInt64,
    device_id      UInt64,
    category_id    UInt64,
    occurred_at    DateTime64(3),
    schema_version Int32,
    event_data     String
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'events.collected',
    kafka_group_name = 'clickhouse-events-collected',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 1,
    kafka_max_block_size = 10000,
    -- Spring Kafka JsonSerializer 가 camelCase 로 produce 하므로 ClickHouse 측 받을 때
    -- input_format_json_named_tuples_as_objects + JSONEachRow 필드명 정확히 매칭 필요.
    input_format_skip_unknown_fields = 1,
    input_format_import_nested_json = 0;

-- 2) MaterializedView — Kafka queue 에서 받은 row 를 events MergeTree 에 흘려보냄.
--    event_id 는 Kafka 메시지에 없으므로 0 으로 (segment build SQL 은 event_id 사용 안 함).
--    created_at = now(), updated_at = NULL.
CREATE MATERIALIZED VIEW IF NOT EXISTS butterfly.events_cdc_mv
TO butterfly.events
AS SELECT
    0           AS event_id,
    project_id,
    user_id,
    category_id,
    device_id,
    event_uuid,
    occurred_at,
    schema_version,
    event_data,
    now()       AS created_at,
    CAST(NULL AS Nullable(DateTime)) AS updated_at
FROM butterfly.events_kafka_queue;

-- 검증:
--   SELECT count() FROM butterfly.events;                 -- 적재 row 총 수
--   SELECT max(occurred_at) FROM butterfly.events;        -- 가장 최근 event 시각
--   SELECT count() FROM system.kafka_consumers
--     WHERE database='butterfly' AND table='events_kafka_queue';   -- consumer 활성 확인
