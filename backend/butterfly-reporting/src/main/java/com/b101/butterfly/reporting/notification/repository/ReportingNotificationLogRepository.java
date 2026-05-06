package com.b101.butterfly.reporting.notification.repository;

import com.b101.butterfly.reporting.aggregation.dto.DailyCampaignCountRow;
import com.b101.butterfly.reporting.aggregation.dto.DailyFailureCountRow;
import com.b101.butterfly.reporting.aggregation.dto.DailyTemplateCountRow;
import com.b101.butterfly.reporting.aggregation.dto.HourlyCountRow;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 발송 로그 저장소. 멱등성 키로 기존 row 존재 여부를 빠르게 확인할 수 있도록
 * {@link #findByMessageKey(String)}을 노출한다 — 컨슈머는 이 키로 dedup을 수행한다.
 *
 * <p>집계 도메인이 사용하는 두 가지 카운트 쿼리도 함께 노출한다:
 * <ul>
 *     <li>{@link #countsForReconciliation} — S14P31B101-126 정합성 검증, 회차×채널 단위</li>
 *     <li>{@link #aggregateHourly} — S14P31B101-124 hourly 집계, 캠페인×채널 단위 시간 윈도우 합산</li>
 * </ul>
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

    /**
     * Hourly 집계용 — 시간 윈도우 안의 발송 로그를 캠페인×채널 단위로 GROUP BY.
     *
     * <p>윈도우는 {@code [from, to)} 반열림 구간으로 처리해 인접 버킷이 중복 카운트되지 않게 한다.
     * 기준 시각은 `notification_logs.created_at` (적재 시각) — 이는 발송 시점과 가까운 시간이고
     * 인덱스(`idx_notification_logs_project_time`)가 활용된다.
     *
     * <p>같은 캠페인·채널이라도 다른 프로젝트로 들어온 row가 있다면 별개 row로 그룹된다 —
     * `project_id`도 자연 키의 일부로 묶는다.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.aggregation.dto.HourlyCountRow(
                n.projectId,
                n.campaignId,
                n.channel,
                SUM(CASE WHEN n.status =
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.PENDING
                    THEN 1 ELSE 0 END),
                SUM(CASE WHEN n.status IN (
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.SENT,
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.DELIVERED
                    ) THEN 1 ELSE 0 END),
                SUM(CASE WHEN n.status =
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.DELIVERED
                    THEN 1 ELSE 0 END),
                SUM(CASE WHEN n.status =
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.FAILED
                    THEN 1 ELSE 0 END)
            )
            FROM NotificationLog n
            WHERE n.createdAt >= :from
              AND n.createdAt < :to
            GROUP BY n.projectId, n.campaignId, n.channel
            """)
    List<HourlyCountRow> aggregateHourly(LocalDateTime from, LocalDateTime to);

    /**
     * Daily 롤업용 — 하루치 notification_logs를 캠페인×채널 단위로 GROUP BY.
     * user_action_logs의 open/click/convert는 DailyAggregator가 별도 조회 후 병합한다.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.aggregation.dto.DailyCampaignCountRow(
                n.projectId,
                n.campaignId,
                n.channel,
                SUM(CASE WHEN n.status IN (
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.SENT,
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.DELIVERED
                    ) THEN 1 ELSE 0 END),
                SUM(CASE WHEN n.status =
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.DELIVERED
                    THEN 1 ELSE 0 END),
                SUM(CASE WHEN n.status =
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.FAILED
                    THEN 1 ELSE 0 END),
                0L, 0L, 0L
            )
            FROM NotificationLog n
            WHERE n.createdAt >= :from
              AND n.createdAt < :to
            GROUP BY n.projectId, n.campaignId, n.channel
            """)
    List<DailyCampaignCountRow> aggregateDailyCampaign(LocalDateTime from, LocalDateTime to);

    /**
     * Daily 롤업용 — 하루치 notification_logs를 템플릿×채널 단위로 GROUP BY.
     * messageTemplateId가 null인 row는 제외된다.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.aggregation.dto.DailyTemplateCountRow(
                n.projectId,
                n.messageTemplateId,
                n.channel,
                COUNT(DISTINCT n.campaignId),
                SUM(CASE WHEN n.status IN (
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.SENT,
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.DELIVERED
                    ) THEN 1 ELSE 0 END),
                SUM(CASE WHEN n.status =
                        com.b101.butterfly.reporting.notification.entity.NotificationStatus.DELIVERED
                    THEN 1 ELSE 0 END),
                0L, 0L, 0L
            )
            FROM NotificationLog n
            WHERE n.createdAt >= :from
              AND n.createdAt < :to
              AND n.messageTemplateId IS NOT NULL
            GROUP BY n.projectId, n.messageTemplateId, n.channel
            """)
    List<DailyTemplateCountRow> aggregateDailyTemplate(LocalDateTime from, LocalDateTime to);

    /**
     * Daily 롤업용 — 하루치 FAILED 로그를 프로젝트×채널×에러코드 단위로 GROUP BY.
     * errorCode가 null인 row는 제외된다.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.aggregation.dto.DailyFailureCountRow(
                n.projectId,
                n.channel,
                n.errorCode,
                COUNT(n),
                COUNT(DISTINCT n.campaignId),
                COUNT(DISTINCT n.userId),
                MIN(n.createdAt),
                MAX(n.createdAt)
            )
            FROM NotificationLog n
            WHERE n.createdAt >= :from
              AND n.createdAt < :to
              AND n.status = com.b101.butterfly.reporting.notification.entity.NotificationStatus.FAILED
              AND n.errorCode IS NOT NULL
            GROUP BY n.projectId, n.channel, n.errorCode
            """)
    List<DailyFailureCountRow> aggregateDailyFailure(LocalDateTime from, LocalDateTime to);
}
