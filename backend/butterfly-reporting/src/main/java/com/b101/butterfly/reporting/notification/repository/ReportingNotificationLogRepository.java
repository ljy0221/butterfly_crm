package com.b101.butterfly.reporting.notification.repository;

import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * 발송 로그 저장소. 멱등성 키로 기존 row 존재 여부를 빠르게 확인할 수 있도록
 * {@link #findByMessageKey(String)}을 노출한다 — 컨슈머는 이 키로 dedup을 수행한다.
 *
 * <p>S14P31B101-126 정합성 검증을 위해 회차×채널 단위 카운트
 * ({@link #countsForReconciliation}) 도 노출한다 — 한 번의 쿼리로 actual/sent/failed
 * 카운트를 합산해 N+1을 회피한다.
 */
public interface ReportingNotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByMessageKey(String messageKey);

    /**
     * 정합성 검증용 카운트 — 회차×채널 단위 actual/sent/failed 합산.
     *
     * <p>SENT/DELIVERED 는 sent_count, FAILED 는 failed_count, 전체 row 수가 actual_count.
     * 카운트는 SQL 측에서 SUM(CASE WHEN ... THEN 1 ELSE 0 END) 으로 한 번에 계산.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.notification.repository.NotificationCountSummary(
                COUNT(n),
                SUM(CASE WHEN n.status IN (
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.SENT,
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.DELIVERED
                    ) THEN 1 ELSE 0 END),
                SUM(CASE WHEN n.status =
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.FAILED
                    THEN 1 ELSE 0 END)
            )
            FROM NotificationLog n
            WHERE n.campaignExecutionId = :executionId
              AND n.channel = :channel
            """)
    NotificationCountSummary countsForReconciliation(Long executionId, Channel channel);
}
