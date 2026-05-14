package com.b101.butterfly.event.adapter.in.segment;

import com.b101.butterfly.event.config.ClickHouseConfig.ClickHouseAccess;
import com.b101.butterfly.segment.port.UserDirectoryReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * UserDirectoryReader 의 ClickHouse 구현 (S14P31B101-275).
 *
 * <p>{@code butterfly.clickhouse.enabled=true} 일 때 활성 + {@code @Primary} 로 등록되어 기본 MySQL
 * 어댑터를 가린다.
 *
 * <p><b>흐름</b>:
 * <ol>
 *   <li>{@code ConditionTreeToSqlConverter} 가 ClickHouse dialect 로 만든 SELECT 를 ClickHouse 에 발사
 *       (column store + JSONExtractString — 100M scan 이 수 초)</li>
 *   <li>결과 user_id list 를 {@link #CHUNK_SIZE} 단위로 끊어 MySQL {@code segment_members} 에 batch
 *       INSERT IGNORE — cross-DB 라 단일 INSERT-SELECT 직행 불가</li>
 * </ol>
 *
 * <p>ClickHouse 측 template 은 {@link ClickHouseAccess} wrapper 를 통해 받는다 — Spring Boot 의
 * autoconfig 가 단일 DataSource / NamedParameterJdbcTemplate 빈만 인식하도록 type-pollution 회피.
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "butterfly.clickhouse.enabled", havingValue = "true")
public class ClickHouseUserDirectoryReaderAdapter implements UserDirectoryReader {

    /** 매칭 user_id 를 MySQL 로 옮길 때 한 batch 의 크기. */
    private static final int CHUNK_SIZE = 1000;

    private static final String INSERT_SQL =
            "INSERT IGNORE INTO segment_members (segment_id, user_id, matched_at, expires_at) "
                    + "VALUES (:segmentId, :userId, :matchedAt, :expiresAt)";

    private final NamedParameterJdbcTemplate clickHouseJdbcTemplate;
    private final NamedParameterJdbcTemplate mysqlJdbcTemplate;

    public ClickHouseUserDirectoryReaderAdapter(
            ClickHouseAccess clickHouseAccess,
            NamedParameterJdbcTemplate mysqlJdbcTemplate) {
        this.clickHouseJdbcTemplate = clickHouseAccess.jdbcTemplate();
        this.mysqlJdbcTemplate = mysqlJdbcTemplate;
    }

    @Override
    public List<Long> queryUserIds(String sql, Map<String, Object> params) {
        return clickHouseJdbcTemplate.queryForList(sql, params, Long.class);
    }

    @Override
    public long materializeUserMatchesIntoSegment(
            Long segmentId, String selectSql, Map<String, Object> params) {
        long start = System.nanoTime();
        List<Long> userIds = clickHouseJdbcTemplate.queryForList(selectSql, params, Long.class);
        long selectMillis = (System.nanoTime() - start) / 1_000_000;
        log.info(
                "ClickHouse SELECT segmentId={} returned {} user_ids in {} ms",
                segmentId,
                userIds.size(),
                selectMillis);

        if (userIds.isEmpty()) {
            return 0L;
        }

        long inserted = 0L;
        LocalDateTime matchedAt = LocalDateTime.now();
        List<SqlParameterSource> buffer = new ArrayList<>(CHUNK_SIZE);
        long insertStart = System.nanoTime();
        for (Long userId : userIds) {
            buffer.add(
                    new MapSqlParameterSource()
                            .addValue("segmentId", segmentId)
                            .addValue("userId", userId)
                            .addValue("matchedAt", matchedAt)
                            .addValue("expiresAt", null));
            if (buffer.size() >= CHUNK_SIZE) {
                inserted += sumBatch(buffer);
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) {
            inserted += sumBatch(buffer);
        }
        long insertMillis = (System.nanoTime() - insertStart) / 1_000_000;
        log.info(
                "MySQL segment_members INSERT segmentId={} inserted={} in {} ms",
                segmentId,
                inserted,
                insertMillis);
        return inserted;
    }

    private long sumBatch(List<SqlParameterSource> buffer) {
        int[] updated =
                mysqlJdbcTemplate.batchUpdate(
                        INSERT_SQL, buffer.toArray(SqlParameterSource[]::new));
        return Arrays.stream(updated).sum();
    }
}
