package com.b101.butterfly.reporting.event.repository;

import com.b101.butterfly.reporting.aggregation.dto.DailyActionCountRow;
import com.b101.butterfly.reporting.event.entity.UserActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 반응 로그 저장소. Append-only — INSERT/SELECT 만 사용한다 (UPDATE/DELETE는
 * Hibernate {@code @Immutable} 로 차단된다).
 */
public interface UserActionLogRepository extends JpaRepository<UserActionLog, Long> {

    /**
     * Daily 롤업용 — 하루치 user_action_logs를 campaignId 단위로 OPEN/CLICK/CONVERT 집계.
     * action_at 기준으로 윈도우를 잡는다 (사용자 행동 발생 시각 — KPI 정의서 §3).
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.aggregation.dto.DailyActionCountRow(
                a.campaignId,
                SUM(CASE WHEN a.actionType =
                        com.b101.butterfly.reporting.event.entity.ActionType.OPEN
                    THEN 1 ELSE 0 END),
                SUM(CASE WHEN a.actionType =
                        com.b101.butterfly.reporting.event.entity.ActionType.CLICK
                    THEN 1 ELSE 0 END),
                SUM(CASE WHEN a.actionType =
                        com.b101.butterfly.reporting.event.entity.ActionType.CONVERT
                    THEN 1 ELSE 0 END)
            )
            FROM UserActionLog a
            WHERE a.actionAt >= :from
              AND a.actionAt < :to
            GROUP BY a.campaignId
            """)
    List<DailyActionCountRow> aggregateDailyActions(LocalDateTime from, LocalDateTime to);
}
