package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.aggregation.dto.DailyProjectCountRow;
import com.b101.butterfly.reporting.aggregation.entity.CampaignStatsDaily;
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
}
