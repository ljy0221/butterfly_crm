package com.b101.butterfly.reporting.notification.repository;

import com.b101.butterfly.reporting.aggregation.dto.DailyFailureCountRow;
import com.b101.butterfly.reporting.aggregation.dto.DailyTemplateCountRow;
import com.b101.butterfly.reporting.aggregation.dto.HourlyCountRow;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 발송 로그 저장소. 멱등성 키로 기존 row 존재 여부를 빠르게 확인할 수 있도록
 * {@link #findByMessageKey(String)}을 노출한다 — 컨슈머는 이 키로 dedup을 수행한다.
 *
 * <p>집계 도메인이 사용하는 카운트 쿼리들:
 * <ul>
 *     <li>{@link #countsForReconciliation} — S14P31B101-126 정합성 검증, 회차×채널 단위</li>
 *     <li>{@link #aggregateHourly} — S14P31B101-124 hourly 집계, 캠페인×채널 단위 시간 윈도우 합산</li>
 *     <li>{@link #aggregateTemplateDaily} — S14P31B101-191 daily 템플릿 집계</li>
 *     <li>{@link #aggregateFailureDaily} — S14P31B101-191 daily 실패 원인 집계</li>
 * </ul>
 */
public interface ReportingNotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByMessageKey(String messageKey);

    /**
     * 자동 배치(S14P31B101-187 PR-2) 가 "실제 발송 row 가 1건이라도 있는 회차" 만 검증 대상으로
     * 좁히기 위한 빠른 카운트.
     *
     * <p>이서연 합의(2026-05-08)에 따라 어댑터 JPQL은 그대로 두고 caller-side 에서
     * {@code count > 0} 필터링을 적용한다 — `send_started_at IS NULL` 회차의 노이즈 불일치 적재
     * 방지. "발송 시작은 됐는데 워커가 stuck 인 회차" 모니터링은 별도 메트릭(stale execution
     * detection)으로 분리, 본 메서드는 reconciliation 전용 1차 필터.
     *
     * <p>스케줄러가 회차당 1회 호출하므로 인덱스 활용이 중요 —
     * `idx_notification_logs_project_time` 만으로는 부족할 수 있어 대량 회차 처리 시
     * `(campaign_execution_id, channel)` 복합 인덱스 유무를 점검한다 (PR-3 운영 점검 항목).
     */
    long countByCampaignExecutionIdAndChannel(Long campaignExecutionId, Channel channel);

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
     * Daily 템플릿 집계 — 날짜 범위 안의 발송 로그를 message_template_id×channel 단위로 GROUP BY.
     *
     * <p>message_template_id가 null인 row(템플릿 없이 발송된 경우)는 제외한다.
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
                0L,
                0L,
                0L
            )
            FROM NotificationLog n
            WHERE n.createdAt >= :from
              AND n.createdAt < :to
              AND n.messageTemplateId IS NOT NULL
            GROUP BY n.projectId, n.messageTemplateId, n.channel
            """)
    List<DailyTemplateCountRow> aggregateTemplateDaily(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Daily 실패 원인 집계 — 날짜 범위 안의 FAILED row를 project×channel×error_code 단위로 GROUP BY.
     *
     * <p>error_code가 null인 row는 'UNKNOWN'으로 대체한다.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.aggregation.dto.DailyFailureCountRow(
                n.projectId,
                n.channel,
                COALESCE(n.errorCode, 'UNKNOWN'),
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
            GROUP BY n.projectId, n.channel, COALESCE(n.errorCode, 'UNKNOWN')
            """)
    List<DailyFailureCountRow> aggregateFailureDaily(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
