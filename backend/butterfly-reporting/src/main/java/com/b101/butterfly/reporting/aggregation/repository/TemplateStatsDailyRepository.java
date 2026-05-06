package com.b101.butterfly.reporting.aggregation.repository;

import com.b101.butterfly.reporting.aggregation.entity.TemplateStatsDaily;
import com.b101.butterfly.reporting.notification.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TemplateStatsDailyRepository extends JpaRepository<TemplateStatsDaily, Long> {

    Optional<TemplateStatsDaily> findByMessageTemplateIdAndChannelAndBucketDate(
            Long messageTemplateId, Channel channel, LocalDate bucketDate);

    List<TemplateStatsDaily> findByBucketDate(LocalDate bucketDate);

    @Modifying
    @Query(value = "DELETE FROM template_stats_daily WHERE bucket_date = :bucketDate",
            nativeQuery = true)
    int deleteByBucketDate(@Param("bucketDate") LocalDate bucketDate);

    @Modifying
    @Query(value = """
            INSERT INTO template_stats_daily
                (project_id, message_template_id, channel, bucket_date,
                 used_campaign_count, sent_count, delivered_count,
                 open_count, click_count, convert_count,
                 open_rate, click_rate, convert_rate,
                 updated_at)
            VALUES
                (:projectId, :messageTemplateId, :channel, :bucketDate,
                 :usedCampaignCount, :sentCount, :deliveredCount,
                 :openCount, :clickCount, :convertCount,
                 CASE WHEN :deliveredCount = 0 THEN NULL ELSE ROUND(:openCount * 100.0 / :deliveredCount, 2) END,
                 CASE WHEN :openCount = 0      THEN NULL ELSE ROUND(:clickCount * 100.0 / :openCount, 2) END,
                 CASE WHEN :sentCount = 0      THEN NULL ELSE ROUND(:convertCount * 100.0 / :sentCount, 2) END,
                 CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                used_campaign_count = VALUES(used_campaign_count),
                sent_count          = VALUES(sent_count),
                delivered_count     = VALUES(delivered_count),
                open_count          = VALUES(open_count),
                click_count         = VALUES(click_count),
                convert_count       = VALUES(convert_count),
                open_rate           = VALUES(open_rate),
                click_rate          = VALUES(click_rate),
                convert_rate        = VALUES(convert_rate),
                updated_at          = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(
            @Param("projectId") Long projectId,
            @Param("messageTemplateId") Long messageTemplateId,
            @Param("channel") String channel,
            @Param("bucketDate") LocalDate bucketDate,
            @Param("usedCampaignCount") int usedCampaignCount,
            @Param("sentCount") int sentCount,
            @Param("deliveredCount") int deliveredCount,
            @Param("openCount") int openCount,
            @Param("clickCount") int clickCount,
            @Param("convertCount") int convertCount
    );
}
