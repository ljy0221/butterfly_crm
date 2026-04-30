package com.b101.butterfly.customer.repository;

import com.b101.butterfly.customer.entity.EventDailyAgg;
import com.b101.butterfly.customer.entity.EventDailyAggId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface EventDailyAggRepository extends JpaRepository<EventDailyAgg, EventDailyAggId> {

    /**
     * (logDate, projectId, categoryId, userId) row가 있으면 event_count +1, last_at 갱신.
     * 없으면 신규 row insert (event_count=1, first_at=last_at=occurredAt).
     */
    @Modifying
    @Query(value = """
            INSERT INTO event_daily_agg
                (log_date, project_id, category_id, user_id, event_count, first_at, last_at, updated_at)
            VALUES
                (:logDate, :projectId, :categoryId, :userId, 1, :occurredAt, :occurredAt, NOW())
            ON DUPLICATE KEY UPDATE
                event_count = event_count + 1,
                last_at = GREATEST(last_at, :occurredAt),
                updated_at = NOW()
            """, nativeQuery = true)
    void upsertIncrement(
            @Param("logDate") LocalDate logDate,
            @Param("projectId") Long projectId,
            @Param("categoryId") Long categoryId,
            @Param("userId") Long userId,
            @Param("occurredAt") LocalDateTime occurredAt
    );
}
