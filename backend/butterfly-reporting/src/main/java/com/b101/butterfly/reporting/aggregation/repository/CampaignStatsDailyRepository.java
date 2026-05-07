package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.aggregation.dto.DailyProjectCountRow;
import com.b101.butterfly.reporting.aggregation.entity.CampaignStatsDaily;
import com.b101.butterfly.reporting.notification.entity.Channel;
import com.b101.butterfly.reporting.report.dto.CampaignChannelStatsRow;
import com.b101.butterfly.reporting.report.dto.FunnelCountRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * campaign_stats_daily 저장소.
 *
 * <p>S14P31B101-191: hourly 집계 결과를 daily로 롤업하는 UPSERT native 쿼리를 노출한다.
 * 비율 지표(delivery_rate 등)는 분모가 0이면 NULL — SQL NULLIF 활용.
 */
public interface CampaignStatsDailyRepository extends JpaRepository<CampaignStatsDaily, Long> {

    Optional<CampaignStatsDaily> findByCampaignIdAndChannelAndBucketDate(
            Long campaignId,
            com.b101.butterfly.reporting.notification.entity.Channel channel,
            LocalDate bucketDate);

    /**
     * 자연 키 {@code (campaign_id, channel, bucket_date)} 기반 UPSERT.
     *
     * <p>campaign_stats_hourly를 날짜 단위로 합산한 결과를 받아 덮어쓴다.
     * 비율 지표는 NULLIF로 분모 0 방어 — KPI 정의서 §3.
     */
    @Modifying
    @Query(value = """
            INSERT INTO campaign_stats_daily
                (project_id, campaign_id, channel, bucket_date,
                 sent_count, delivered_count, failed_count,
                 open_count, click_count, convert_count,
                 delivery_rate, open_rate, click_rate, convert_rate,
                 updated_at)
            VALUES
                (:projectId, :campaignId, :channel, :bucketDate,
                 :sentCount, :deliveredCount, :failedCount,
                 :openCount, :clickCount, :convertCount,
                 CASE WHEN :sentCount = 0      THEN NULL ELSE ROUND(:deliveredCount * 100.0 / :sentCount, 2) END,
                 CASE WHEN :deliveredCount = 0 THEN NULL ELSE ROUND(:openCount * 100.0 / :deliveredCount, 2) END,
                 CASE WHEN :openCount = 0      THEN NULL ELSE ROUND(:clickCount * 100.0 / :openCount, 2) END,
                 CASE WHEN :sentCount = 0      THEN NULL ELSE ROUND(:convertCount * 100.0 / :sentCount, 2) END,
                 CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                sent_count      = VALUES(sent_count),
                delivered_count = VALUES(delivered_count),
                failed_count    = VALUES(failed_count),
                open_count      = VALUES(open_count),
                click_count     = VALUES(click_count),
                convert_count   = VALUES(convert_count),
                delivery_rate   = VALUES(delivery_rate),
                open_rate       = VALUES(open_rate),
                click_rate      = VALUES(click_rate),
                convert_rate    = VALUES(convert_rate),
                updated_at      = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(
            @Param("projectId") Long projectId,
            @Param("campaignId") Long campaignId,
            @Param("channel") String channel,
            @Param("bucketDate") LocalDate bucketDate,
            @Param("sentCount") int sentCount,
            @Param("deliveredCount") int deliveredCount,
            @Param("failedCount") int failedCount,
            @Param("openCount") int openCount,
            @Param("clickCount") int clickCount,
            @Param("convertCount") int convertCount
    );

    /**
     * force 재집계용 — 특정 날짜의 모든 row 삭제.
     */
    @Modifying
    @Query(value = "DELETE FROM campaign_stats_daily WHERE bucket_date = :bucketDate",
            nativeQuery = true)
    int deleteByBucketDate(@Param("bucketDate") LocalDate bucketDate);

    /**
     * project_stats_daily 롤업용 — 특정 날짜의 campaign_stats_daily를 project 단위로 합산.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.aggregation.dto.DailyProjectCountRow(
                c.projectId,
                SUM(CAST(c.sentCount AS long)),
                SUM(CAST(c.deliveredCount AS long)),
                SUM(CAST(c.failedCount AS long)),
                SUM(CAST(c.openCount AS long)),
                SUM(CAST(c.clickCount AS long)),
                SUM(CAST(c.convertCount AS long)),
                COUNT(DISTINCT c.campaignId)
            )
            FROM CampaignStatsDaily c
            WHERE c.bucketDate = :bucketDate
            GROUP BY c.projectId
            """)
    List<DailyProjectCountRow> rollupProjectByDate(@Param("bucketDate") LocalDate bucketDate);

    /**
     * S14P31B101-92 — 기간 내 (campaignId, channel) 단위로 카운트 합산.
     *
     * <p>segment-channel 매트릭스가 본 결과를 받아 캠페인 ID를 세그먼트로 환원하고
     * 다시 (segmentId, channel) 단위로 재합산한다. DB에서는 segment_id를 모르므로
     * 캠페인 단위까지만 집계 후 메모리에서 그룹화하는 책임을 서비스에 둔다.
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.report.dto.CampaignChannelStatsRow(
                c.campaignId,
                c.channel,
                SUM(CAST(c.sentCount AS long)),
                SUM(CAST(c.deliveredCount AS long)),
                SUM(CAST(c.failedCount AS long)),
                SUM(CAST(c.openCount AS long)),
                SUM(CAST(c.clickCount AS long)),
                SUM(CAST(c.convertCount AS long))
            )
            FROM CampaignStatsDaily c
            WHERE c.projectId = :projectId
              AND c.bucketDate >= :from
              AND c.bucketDate <= :to
            GROUP BY c.campaignId, c.channel
            """)
    List<CampaignChannelStatsRow> aggregateByProjectAndDateRange(
            @Param("projectId") Long projectId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /**
     * S14P31B101-90 — 캠페인 단계 펀널용 합산 카운트.
     *
     * <p>{@code campaignId} / {@code channel}이 null이면 해당 차원 필터 미적용.
     * COALESCE로 데이터 0건 시 SUM이 NULL이 되는 것을 방지 (record 생성자가 long 받음).
     */
    @Query("""
            SELECT new com.b101.butterfly.reporting.report.dto.FunnelCountRow(
                COALESCE(SUM(CAST(c.sentCount AS long)), 0L),
                COALESCE(SUM(CAST(c.deliveredCount AS long)), 0L),
                COALESCE(SUM(CAST(c.openCount AS long)), 0L),
                COALESCE(SUM(CAST(c.clickCount AS long)), 0L),
                COALESCE(SUM(CAST(c.convertCount AS long)), 0L)
            )
            FROM CampaignStatsDaily c
            WHERE c.projectId = :projectId
              AND c.bucketDate >= :from
              AND c.bucketDate <= :to
              AND (:campaignId IS NULL OR c.campaignId = :campaignId)
              AND (:channel IS NULL OR c.channel = :channel)
            """)
    FunnelCountRow aggregateFunnel(
            @Param("projectId") Long projectId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("campaignId") Long campaignId,
            @Param("channel") Channel channel
    );
}
