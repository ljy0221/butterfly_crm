package com.b101.butterfly.reporting.notification.repository;

import com.b101.butterfly.reporting.notification.messaging.NotificationSendResultPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * IDENTITY 키 전략으로 인해 Hibernate JDBC 배치가 비활성화되는 문제를 우회하는
 * 네이티브 bulk INSERT 구현체.
 *
 * <p>GenerationType.IDENTITY는 INSERT 후 generated key를 DB에서 읽어야 하므로
 * Hibernate가 statement를 하나씩 실행한다 (batch_size 설정이 무의미해짐).
 * NamedParameterJdbcTemplate.batchUpdate()는 JPA를 거치지 않으므로 이 제약이 없다.
 *
 * <p>멱등성: message_key UNIQUE 제약(V13)이 중복 INSERT를 DB 레벨에서 차단한다.
 * batch 중 일부가 UNIQUE 위반 시 BatchUpdateException이 발생하며,
 * 호출자(컨슈머)가 이를 잡아 처리한다.
 */
@Repository
@RequiredArgsConstructor
public class NotificationLogBulkRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
            INSERT IGNORE INTO notification_logs
                (project_id, campaign_id, campaign_execution_id, message_template_id,
                 user_id, device_id, channel, status, error_code, error_message,
                 sent_at, delivered_at, created_at, message_key)
            VALUES
                (:projectId, :campaignId, :campaignExecutionId, :messageTemplateId,
                 :userId, :deviceId, :channel, :status, :errorCode, :errorMessage,
                 :sentAt, :deliveredAt, :createdAt, :messageKey)
            """;

    /**
     * 페이로드 리스트를 단일 배치 INSERT로 적재한다.
     *
     * <p>INSERT IGNORE를 사용해 message_key 중복 시 해당 row만 건너뛰고 나머지를 정상 삽입한다.
     * DataIntegrityViolationException이 발생하지 않으므로 배치 전체가 롤백되지 않는다.
     *
     * @param payloads 삽입할 페이로드 목록
     * @return 실제 삽입된 row 수 배열
     */
    public int[] bulkInsert(List<NotificationSendResultPayload> payloads) {
        LocalDateTime now = LocalDateTime.now();
        MapSqlParameterSource[] params = payloads.stream()
                .map(p -> new MapSqlParameterSource()
                        .addValue("projectId", p.projectId())
                        .addValue("campaignId", p.campaignId())
                        .addValue("campaignExecutionId", p.campaignExecutionId())
                        .addValue("messageTemplateId", p.messageTemplateId())
                        .addValue("userId", p.userId())
                        .addValue("deviceId", p.deviceId())
                        .addValue("channel", p.channel() != null ? p.channel().name() : null)
                        .addValue("status", p.status() != null ? p.status().name() : null)
                        .addValue("errorCode", p.errorCode())
                        .addValue("errorMessage", p.errorMessage())
                        .addValue("sentAt", p.sentAt() != null ? Timestamp.valueOf(p.sentAt()) : null)
                        .addValue("deliveredAt", p.deliveredAt() != null ? Timestamp.valueOf(p.deliveredAt()) : null)
                        .addValue("createdAt", Timestamp.valueOf(now))
                        .addValue("messageKey", p.messageKey()))
                .toArray(MapSqlParameterSource[]::new);

        return jdbcTemplate.batchUpdate(INSERT_SQL, params);
    }
}
