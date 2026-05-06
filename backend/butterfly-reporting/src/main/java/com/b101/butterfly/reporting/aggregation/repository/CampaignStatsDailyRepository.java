package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.aggregation.entity.CampaignStatsDaily;
import com.b101.butterfly.reporting.notification.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CampaignStatsDailyRepository extends JpaRepository<CampaignStatsDaily, Long> {

    Optional<CampaignStatsDaily> findByCampaignIdAndChannelAndBucketDate(
            Long campaignId, Channel channel, LocalDate bucketDate);

    List<CampaignStatsDaily> findByBucketDate(LocalDate bucketDate);

    @Modifying
    @Query(value = "DELETE FROM campaign_stats_daily WHERE bucket_date = :bucketDate",
            nativeQuery = true)
    int deleteByBucketDate(@Param("bucketDate") LocalDate bucketDate);

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
                 :deliveryRate, :openRate, :clickRate, :convertRate,
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
            @Param("convertCount") int convertCount,
            @Param("deliveryRate") BigDecimal deliveryRate,
            @Param("openRate") BigDecimal openRate,
            @Param("clickRate") BigDecimal clickRate,
            @Param("convertRate") BigDecimal convertRate
    );
}
