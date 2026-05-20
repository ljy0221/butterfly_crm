package com.b101.butterfly.reporting.event.repository;

import com.b101.butterfly.reporting.aggregation.dto.HourlyActionCountRow;
import com.b101.butterfly.reporting.event.entity.UserActionLog;
import com.b101.butterfly.reporting.notification.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 반응 로그 저장소. Append-only — INSERT/SELECT 만 사용한다 (UPDATE/DELETE는
 * Hibernate {@code @Immutable} 로 차단된다).
 */
public interface UserActionLogRepository extends JpaRepository<UserActionLog, Long> {

    @Query(value = """
            SELECT
                ual.project_id AS projectId,
                ual.campaign_id AS campaignId,
                nl.channel AS channel,
                COUNT(DISTINCT CASE WHEN ual.action_type = 'OPEN' THEN ual.log_id END) AS openCount,
                COUNT(DISTINCT CASE WHEN ual.action_type = 'CLICK' THEN ual.log_id END) AS clickCount,
                COUNT(DISTINCT CASE WHEN ual.action_type = 'CONVERT' THEN ual.log_id END) AS convertCount
            FROM user_action_logs ual
            JOIN notification_logs nl ON nl.log_id = ual.log_id
            WHERE ual.action_at >= :from
              AND ual.action_at < :to
            GROUP BY ual.project_id, ual.campaign_id, nl.channel
            """, nativeQuery = true)
    List<HourlyActionCountProjection> aggregateHourlyRaw(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    default List<HourlyActionCountRow> aggregateHourly(LocalDateTime from, LocalDateTime to) {
        return aggregateHourlyRaw(from, to).stream()
                .map(row -> new HourlyActionCountRow(
                        row.getProjectId(),
                        row.getCampaignId(),
                        Channel.valueOf(row.getChannel()),
                        row.getOpenCount(),
                        row.getClickCount(),
                        row.getConvertCount()
                ))
                .toList();
    }

    interface HourlyActionCountProjection {
        Long getProjectId();

        Long getCampaignId();

        String getChannel();

        Long getOpenCount();

        Long getClickCount();

        Long getConvertCount();
    }
}
