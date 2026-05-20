package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.aggregation.dto.DailyCampaignCountRow;
import com.b101.butterfly.reporting.aggregation.entity.CampaignStatsHourly;
import com.b101.butterfly.reporting.dto.CampaignMetricRow;
import com.b101.butterfly.reporting.dto.ChannelMetricRow;
import com.b101.butterfly.reporting.dto.PeriodSummaryRow;
import com.b101.butterfly.reporting.notification.entity.Channel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * campaign_stats_hourly 저장소.
 *
 * <p>운영 INSERT/UPDATE는 {@link #upsert} 의 native UPSERT 한 방으로 한다 — JPA persist 경로는
 * 자연 키 충돌 처리가 어렵고, MySQL의 `ON DUPLICATE KEY UPDATE` 를 직접 쓰는 것이 멱등성을
 * 가장 명확하게 보장한다.
 *
 */
public interface CampaignStatsHourlyRepository extends JpaRepository<CampaignStatsHourly, Long> {

    Optional<CampaignStatsHourly> findByCampaignIdAndChannelAndBucketHour(
            Long campaignId, Channel channel, LocalDateTime bucketHour);

    /**
     * 자연 키 `(campaign_id, channel, bucket_hour)` 기반 UPSERT.
     *
     * <p>같은 자연 키로 다시 호출되면 카운트 컬럼이 **덮어쓰기**된다 (`= VALUES(...)`).
     * 동일 bucket을 재집계해도 두 배로 누적되지 않는다 — 호출자(Aggregator)가 윈도우 카운트를
     * 매번 새로 산정해 넘기는 모델.
     */
    @Modifying
    @Query(value = """
            INSERT INTO campaign_stats_hourly
                (project_id, campaign_id, channel, bucket_hour,
                 pending_count, sent_count, delivered_count, failed_count,
                 open_count, click_count, convert_count, updated_at)
            VALUES
                (:projectId, :campaignId, :channel, :bucketHour,
                 :pendingCount, :sentCount, :deliveredCount, :failedCount,
                 :openCount, :clickCount, :convertCount, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                pending_count   = VALUES(pending_count),
                sent_count      = VALUES(sent_count),
                delivered_count = VALUES(delivered_count),
                failed_count    = VALUES(failed_count),
                open_count      = VALUES(open_count),
                click_count     = VALUES(click_count),
                convert_count   = VALUES(convert_count),
                updated_at      = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(
            @Param("projectId") Long projectId,
            @Param("campaignId") Long campaignId,
            @Param("channel") String channel,
            @Param("bucketHour") LocalDateTime bucketHour,
            @Param("pendingCount") int pendingCount,
            @Param("sentCount") int sentCount,
            @Param("deliveredCount") int deliveredCount,
            @Param("failedCount") int failedCount,
            @Param("openCount") int openCount,
            @Param("clickCount") int clickCount,
            @Param("convertCount") int convertCount
    );

    /**
     * force 재집계용 — 특정 bucket의 모든 row 삭제. 재집계 전에 호출한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM campaign_stats_hourly WHERE bucket_hour = :bucketHour",
            nativeQuery = true)
    int deleteByBucketHour(@Param("bucketHour") LocalDateTime bucketHour);

    /**
     * daily 롤업용 — 특정 날짜의 hourly row를 캠페인×채널 단위로 합산한다.
     *
     * <p>bucket_hour가 해당 날짜(00:00 ~ 다음날 00:00 미만) 범위에 속하는 row들을 GROUP BY.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.aggregation.dto.DailyCampaignCountRow(
                h.projectId,
                h.campaignId,
                h.channel,
                SUM(CAST(h.sentCount AS long)),
                SUM(CAST(h.deliveredCount AS long)),
                SUM(CAST(h.failedCount AS long)),
                SUM(CAST(h.openCount AS long)),
                SUM(CAST(h.clickCount AS long)),
                SUM(CAST(h.convertCount AS long))
            )
            FROM CampaignStatsHourly h
            WHERE h.bucketHour >= :dayStart
              AND h.bucketHour < :dayEnd
            GROUP BY h.projectId, h.campaignId, h.channel
            """)
    List<DailyCampaignCountRow> rollupByDate(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    /**
     * 대시보드 당일 보강용 — daily 롤업 전 현재 날짜의 hourly row를 project 단위로 합산한다.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.dto.PeriodSummaryRow(
                COALESCE(SUM(CAST(h.sentCount AS long)), 0L),
                COALESCE(SUM(CAST(h.deliveredCount AS long)), 0L),
                COALESCE(SUM(CAST(h.openCount AS long)), 0L),
                COALESCE(SUM(CAST(h.clickCount AS long)), 0L),
                COALESCE(SUM(CAST(h.convertCount AS long)), 0L),
                COALESCE(SUM(CAST(h.failedCount AS long)), 0L),
                COUNT(DISTINCT h.campaignId)
            )
            FROM CampaignStatsHourly h
            WHERE h.projectId = :projectId
              AND h.bucketHour >= :fromInclusive
              AND h.bucketHour < :toExclusive
            """)
    PeriodSummaryRow aggregateProjectPeriod(
            @Param("projectId") Long projectId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    /**
     * 대시보드 최근 캠페인 당일 보강용 — campaign × channel 단위 합산.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.dto.CampaignMetricRow(
                h.campaignId,
                CAST(h.channel AS string),
                CAST(SUM(h.sentCount) AS long),
                CAST(SUM(h.deliveredCount) AS long),
                CAST(SUM(h.openCount) AS long),
                CAST(SUM(h.clickCount) AS long),
                CAST(SUM(h.convertCount) AS long),
                CAST(SUM(h.failedCount) AS long)
            )
            FROM CampaignStatsHourly h
            WHERE h.projectId = :projectId
              AND h.bucketHour >= :fromInclusive
              AND h.bucketHour < :toExclusive
              AND (:channelFilter IS NULL OR h.channel = :channelFilter)
              AND (:campaignIdsFilter IS NULL OR h.campaignId IN :campaignIdsFilter)
            GROUP BY h.campaignId, h.channel
            ORDER BY SUM(h.sentCount) DESC
            """)
    List<CampaignMetricRow> aggregateByCampaign(
            @Param("projectId") Long projectId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("channelFilter") Channel channelFilter,
            @Param("campaignIdsFilter") List<Long> campaignIdsFilter,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(DISTINCT CONCAT(h.campaignId, '-', h.channel))
            FROM CampaignStatsHourly h
            WHERE h.projectId = :projectId
              AND h.bucketHour >= :fromInclusive
              AND h.bucketHour < :toExclusive
              AND (:channelFilter IS NULL OR h.channel = :channelFilter)
              AND (:campaignIdsFilter IS NULL OR h.campaignId IN :campaignIdsFilter)
            """)
    long countCampaignChannelPairs(
            @Param("projectId") Long projectId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("channelFilter") Channel channelFilter,
            @Param("campaignIdsFilter") List<Long> campaignIdsFilter
    );

    /**
     * 대시보드 채널 비중 당일 보강용 — channel 단위 합산.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.dto.ChannelMetricRow(
                h.channel,
                CAST(SUM(h.sentCount) AS long),
                CAST(SUM(h.deliveredCount) AS long),
                CAST(SUM(h.openCount) AS long),
                CAST(SUM(h.clickCount) AS long),
                CAST(SUM(h.convertCount) AS long),
                CAST(SUM(h.failedCount) AS long)
            )
            FROM CampaignStatsHourly h
            WHERE h.projectId = :projectId
              AND h.bucketHour >= :fromInclusive
              AND h.bucketHour < :toExclusive
            GROUP BY h.channel
            ORDER BY SUM(h.sentCount) DESC
            """)
    List<ChannelMetricRow> aggregateByChannel(
            @Param("projectId") Long projectId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );
}
